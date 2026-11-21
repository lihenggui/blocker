// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import be.mygod.librootkotlinx.RootCommandNoResult
import com.merxury.blocker.core.root.RootCommandExecutor
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

internal const val AUTOMATIC_FILE = "blocker-managed-wake"
internal const val PROBE_FILE = "blocker-wake-capability-probe"
data class WakeServiceFilter(val caller: String, val target: String, val service: String, val action: String)

fun interface ManagedWakeReset {
    suspend fun resetFirewall(packageName: String?)
}

fun interface WakeRuleWriter {
    suspend fun write(xml: String?, probe: Boolean)
}

class ManagedWakeRules @Inject constructor(
    private val writer: WakeRuleWriter,
    private val root: RootCommandExecutor,
    @param:ApplicationContext private val context: Context,
) {
    // ResetIfwWorker already authorized direct root for the manual rule store.
    suspend fun resetWithRoot(packageName: String?) {
        root.execute(ResetWakeRules(packageName))
    }

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
class WriteWakeRules(private val xml: String?, private val probe: Boolean = false) : RootCommandNoResult {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        writeManagedWakeFile(xml, probe)
        null
    }
}

@Parcelize
internal data class ResetWakeRules(val packageName: String?) : RootCommandNoResult {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        if (packageName == null) {
            writeManagedWakeFile(null, false)
            writeManagedWakeFile(null, true)
        } else {
            removeManagedWakePackage(packageName)
        }
        null
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

private fun removeManagedWakePackage(packageName: String) {
    val file = File(IfwStorageUtils.ifwFolder, "$AUTOMATIC_FILE.xml")
    try {
        Os.stat(file.path)
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) return
        throw e
    }
    writeManagedWakeFile(withoutWakePackage(file.readText(), packageName), false)
}

fun writeManagedWakeFile(xml: String?, probe: Boolean) {
    val directory = File(IfwStorageUtils.ifwFolder)
    val name = if (probe) PROBE_FILE else AUTOMATIC_FILE
    val target = File(directory, "$name.xml")
    if (xml == null) {
        if (target.exists()) check(target.delete())
    } else if (!target.exists() || target.readText() != xml) {
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File.createTempFile(".blocker-wake-", ".tmp", directory)
        try {
            temporary.outputStream().use { file ->
                file.write(xml.toByteArray())
                file.fd.sync()
            }
            Os.chmod(temporary.path, 0x1a4)
            Os.rename(temporary.path, target.path)
            check(target.readText() == xml)
        } finally {
            temporary.delete()
        }
    }
}
