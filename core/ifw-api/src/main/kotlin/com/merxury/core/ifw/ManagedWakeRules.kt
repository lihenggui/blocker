// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.RootCommand
import be.mygod.librootkotlinx.RootCommandNoResult
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import javax.inject.Inject

private const val AUTOMATIC_FILE = "blocker-managed-wake"
private const val PROBE_FILE = "blocker-wake-capability-probe"
data class WakeServiceFilter(val caller: String, val target: String, val service: String, val action: String)

enum class WakeResetTransport { DIRECT_ROOT, SHIZUKU_ROOT }

fun interface ManagedWakeReset {
    /** Returns whether the requested scope still contains manual rules. */
    suspend fun resetFirewall(packageName: String?, transport: WakeResetTransport): Boolean
}

interface WakeRuleWriter {
    suspend fun write(xml: String?, probe: Boolean)
    suspend fun reset(packageName: String?, transport: WakeResetTransport): Boolean
}

class ManagedWakeRules @Inject constructor(
    private val writer: WakeRuleWriter,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun reset(packageName: String?, transport: WakeResetTransport) = writer.reset(packageName, transport)

    suspend fun supportsPeerFilter(): Boolean = testPeerFilter(context, writer)

    suspend fun replace(paths: Collection<WakeServiceFilter>) {
        writer.write(renderWakeRules(paths), false)
        writer.write(null, true)
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
            ) + if (it.caller == it.target && !forcePackageFilter) {
                listOf(IfwFilter.Sender(SenderType.USER_ID), IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)))
            } else {
                listOf(IfwFilter.SenderPackage(it.caller))
            },
        )
    }
    return if (rules.isEmpty()) null else IfwXmlSerializer().serialize(IfwRules(rules))
}

@Parcelize
class WriteWakeRules(private val owner: String, private val xml: String?, private val probe: Boolean = false) : RootCommandNoResult {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        writeManagedWakeFile(owner, xml, probe)
        null
    }
}

@Parcelize
data class ResetWakeRules(val owner: String, val packageName: String?) : RootCommand<ParcelableBoolean> {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        ParcelableBoolean(resetManagedWakeFiles(owner, packageName))
    }
}

/** The two Intent Firewall files the wake feature owns per installation, kept outside `IfwStore`. */
enum class WakeRuleFileKind {
    /** The rules the wake feature applies. */
    MANAGED,

    /** The temporary rule that tests whether the system honours a sender package filter. */
    PROBE,
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
    }
    // Keep the production file name across upgrades. Other installations own separate files.
    return if (owner == "com.merxury.blocker") base else "$base-$owner"
}

private val PACKAGE_NAME = Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""")

/** Whether [name] is a package name as the wake rule files accept it, for an owner or a target. */
fun isWakePackageName(name: String): Boolean = name.matches(PACKAGE_NAME)

private fun kindOf(probe: Boolean) = if (probe) WakeRuleFileKind.PROBE else WakeRuleFileKind.MANAGED

internal fun isManagedWakeName(name: String): Boolean = name == AUTOMATIC_FILE || name == PROBE_FILE || name.startsWith("$AUTOMATIC_FILE-") || name.startsWith("$PROBE_FILE-")

/** Clears this installation's automatic rules and reports whether manual rules still require direct root. */
fun resetManagedWakeFiles(owner: String, packageName: String?): Boolean {
    if (packageName == null) {
        writeManagedWakeFile(owner, null, false)
        writeManagedWakeFile(owner, null, true)
    } else {
        removeManagedWakePackage(owner, packageName)
    }
    val directory = File(IfwStorageUtils.ifwFolder)
    if (!directory.exists()) return false
    return checkNotNull(directory.list()).any { file ->
        file.endsWith(".xml") && !isManagedWakeName(file.removeSuffix(".xml")) &&
            (packageName == null || file == "$packageName.xml")
    }
}

internal fun withoutWakePackage(xml: String, target: String): String? {
    val original = IfwXmlDeserializer().deserialize(xml)
    val remaining = original.rules.filterNot { rule ->
        rule.filters.filterIsInstance<IfwFilter.ComponentFilter>().singleOrNull()?.name?.substringBefore('/') == target
    }
    if (remaining.size == original.rules.size) return xml
    return if (remaining.isEmpty()) null else IfwXmlSerializer().serialize(IfwRules(remaining))
}

private fun removeManagedWakePackage(owner: String, packageName: String) = removeWakeRuleTarget(owner, WakeRuleFileKind.MANAGED, packageName)

fun writeManagedWakeFile(owner: String, xml: String?, probe: Boolean) = writeWakeRuleFile(owner, kindOf(probe), xml)

/**
 * Replaces [owner]'s [kind] file in [directory] with [xml], so readers only ever see the old or
 * the new file, or deletes it when [xml] is null. Writes nothing when the file already holds
 * [xml], or for null when there is no file, so running it again changes nothing.
 */
fun writeWakeRuleFile(owner: String, kind: WakeRuleFileKind, xml: String?, directory: File = File(IfwStorageUtils.ifwFolder)) {
    val target = File(directory, "${wakeRuleFileName(owner, kind)}.xml")
    if (xml == null) {
        if (target.exists()) check(target.delete())
    } else if (!target.exists() || target.readText() != xml) {
        check(directory.isDirectory || directory.mkdirs())
        replaceIfwFile(target, xml)
        check(target.readText() == xml)
    }
}

/**
 * Removes the rules for services of package [target] from [owner]'s [kind] file in [directory],
 * and the file once no rule is left. A missing file, or one without such rules, stays as it is.
 */
fun removeWakeRuleTarget(owner: String, kind: WakeRuleFileKind, target: String, directory: File = File(IfwStorageUtils.ifwFolder)) {
    val file = File(directory, "${wakeRuleFileName(owner, kind)}.xml")
    try {
        Os.stat(file.path)
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) return
        throw e
    }
    writeWakeRuleFile(owner, kind, withoutWakePackage(file.readText(), target), directory)
}
