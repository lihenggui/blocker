// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val AUTOMATIC_FILE = "blocker-managed-wake"
private const val USER_FILE = "blocker-user-wake"
private const val PROBE_FILE = "blocker-wake-capability-probe"
data class WakeServiceFilter(val caller: String, val target: String, val service: String, val action: String)

fun interface ManagedWakeReset {
    /**
     * Resets the wake feature's rules of [packageName], or all of them when null. Returns false,
     * without a change, when the privileged channel cannot write them.
     */
    suspend fun resetFirewall(packageName: String?): Boolean
}

/**
 * Writes the wake feature's own Intent Firewall files. A call that cannot be made throws; the
 * caller decides beforehand whether the channel may write them.
 */
interface WakeRuleWriter {
    /** Replaces the [kind] file with [xml], or deletes it when [xml] is null. */
    suspend fun write(kind: WakeRuleFileKind, xml: String?)

    /** Removes the rules for [packageName] from the managed and user files, or deletes all files when null. */
    suspend fun reset(packageName: String?)

    /** Removes [packageName] from the given wake rule file. */
    suspend fun removeTarget(kind: WakeRuleFileKind, packageName: String)
}

class ManagedWakeRules @Inject constructor(
    private val writer: WakeRuleWriter,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun reset(packageName: String?) = writer.reset(packageName)

    suspend fun removeTarget(packageName: String) = writer.removeTarget(WakeRuleFileKind.MANAGED, packageName)

    suspend fun supportsPeerFilter(): Boolean = testPeerFilter(context, writer)

    suspend fun replace(paths: Collection<WakeServiceFilter>) {
        writer.write(WakeRuleFileKind.MANAGED, renderWakeRules(paths))
        writer.write(WakeRuleFileKind.PROBE, null)
    }
}

fun renderWakeRules(paths: Collection<WakeServiceFilter>): String? = renderWakeRules(paths, forcePackageFilter = false)

internal fun renderWakeRules(paths: Collection<WakeServiceFilter>, forcePackageFilter: Boolean): String? {
    val rules = paths.distinct().sortedWith(compareBy({ it.caller }, { it.target }, { it.service }, { it.action })).map {
        require(it.caller.isNotBlank() && it.target.isNotBlank() && it.service.isNotBlank())
        IfwRule(
            IfwComponentType.SERVICE,
            log = false,
            filters = listOf(
                IfwFilter.ComponentFilter("${it.target}/${it.service}"),
                IfwFilter.Action(if (it.action.isEmpty()) StringMatcher.IsNull(true) else StringMatcher.Equals(it.action)),
            ) + wakeSenderFilters(it.caller, it.target, forcePackageFilter),
        )
    }
    return if (rules.isEmpty()) null else IfwXmlSerializer().serialize(IfwRules(rules))
}

internal fun wakeSenderFilters(sender: String, target: String, forcePackageFilter: Boolean = false): List<IfwFilter> = when {
    sender == target && !forcePackageFilter -> listOf(IfwFilter.Sender(SenderType.USER_ID), IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)))
    else -> listOf(IfwFilter.SenderPackage(sender))
}

/** The Intent Firewall files the wake feature owns per installation, kept outside `IfwStore`. */
enum class WakeRuleFileKind {
    /** The rules the wake feature applies. */
    MANAGED,

    /** The temporary rule that tests whether the system honours a sender package filter. */
    PROBE,

    /** The rules the user chose for individual wake paths. */
    USER,
}

/**
 * The name, without `.xml`, of [owner]'s [kind] file in the Intent Firewall directory. [owner] is
 * the package name Blocker is installed under.
 *
 * @throws IllegalArgumentException when [owner] is not a package name
 */
fun wakeRuleFileName(owner: String, kind: WakeRuleFileKind): String {
    require(owner.matches(PACKAGE_NAME)) { "Not a package name: $owner" }
    val base = when (kind) {
        WakeRuleFileKind.MANAGED -> AUTOMATIC_FILE
        WakeRuleFileKind.PROBE -> PROBE_FILE
        WakeRuleFileKind.USER -> USER_FILE
    }
    // Keep the production file name across upgrades. Other installations own separate files.
    return if (owner == "com.merxury.blocker") base else "$base-$owner"
}

private val PACKAGE_NAME = Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""")

/** Whether [name] is a package name as the wake rule files accept it, for an owner or a target. */
fun isWakePackageName(name: String): Boolean = name.matches(PACKAGE_NAME)

internal fun isManagedWakeName(name: String): Boolean = listOf(AUTOMATIC_FILE, PROBE_FILE, USER_FILE).any { name == it || name.startsWith("$it-") }

internal fun withoutWakePackage(xml: String, target: String): String? {
    val original = IfwXmlDeserializer().deserialize(xml)
    val remaining = original.rules.filterNot { rule ->
        rule.filters.any { filter ->
            filter is IfwFilter.ComponentFilter && filter.name.substringBefore('/') == target
        }
    }
    if (remaining.size == original.rules.size) return xml
    return if (remaining.isEmpty()) null else IfwXmlSerializer().serializeInOrder(remaining)
}

/**
 * Replaces [owner]'s [kind] file in [directory] with [xml], so readers only ever see the old or
 * the new file, or deletes it when [xml] is null. Writes nothing when the file already holds
 * [xml], or for null when there is no file, so running it again changes nothing.
 */
fun writeWakeRuleFile(owner: String, kind: WakeRuleFileKind, xml: String?, directory: File = File(IfwStorageUtils.ifwFolder)) = mapWakeFileErrors {
    val target = File(directory, "${wakeRuleFileName(owner, kind)}.xml")
    if (xml == null) {
        try {
            Os.remove(target.path)
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.ENOENT) throw e
        }
    } else if (!wakeRuleExists(target) || target.readText() != xml) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            // Use the last mkdir's errno when the File API could not create the directory.
            try {
                Os.mkdir(directory.path, 0x1ff)
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EEXIST || !directory.isDirectory) throw e
            }
        }
        replaceIfwFile(target, xml)
        check(target.readText() == xml)
    }
}

/**
 * Removes the rules for package [target] from [owner]'s [kind] file in [directory],
 * and the file once no rule is left. A missing file, or one without such rules, stays as it is.
 */
fun removeWakeRuleTarget(owner: String, kind: WakeRuleFileKind, target: String, directory: File = File(IfwStorageUtils.ifwFolder)) = mapWakeFileErrors {
    val file = File(directory, "${wakeRuleFileName(owner, kind)}.xml")
    if (!wakeRuleExists(file)) return@mapWakeFileErrors
    writeWakeRuleFile(owner, kind, withoutWakePackage(file.readText(), target), directory)
}

private fun wakeRuleExists(file: File): Boolean = try {
    Os.stat(file.path)
    true
} catch (e: ErrnoException) {
    if (e.errno == OsConstants.ENOENT) false else throw e
}

private inline fun mapWakeFileErrors(block: () -> Unit) {
    try {
        block()
    } catch (e: ErrnoException) {
        throw e.asWakeFileError()
    } catch (e: IOException) {
        throw e.asWakeFileError()
    }
}

private fun Exception.asWakeFileError(): Exception {
    val errno = generateSequence(this as Throwable) { it.cause }.take(4).filterIsInstance<ErrnoException>().firstOrNull()?.errno
    return when (errno) {
        OsConstants.EACCES, OsConstants.EPERM -> SecurityException(message, this)
        else -> this
    }
}
