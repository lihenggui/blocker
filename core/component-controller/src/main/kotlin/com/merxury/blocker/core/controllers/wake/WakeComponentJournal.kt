// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import be.mygod.librootkotlinx.ParcelableString
import be.mygod.librootkotlinx.RootCommand
import com.merxury.blocker.core.model.wake.WakeAccess
import com.merxury.blocker.core.root.RootCommandExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.parcelize.Parcelize
import java.io.File
import javax.inject.Inject

/** Recovery metadata lives under the same privileged lifetime as a PackageManager override. */
class WakeComponentJournal @Inject constructor(
    private val root: RootCommandExecutor,
    private val reader: WakeSystemReader,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun read(): String = access(null)
    suspend fun write(contents: String) {
        access(contents)
    }

    private suspend fun access(replacement: String?): String = when (val access = reader.access()) {
        WakeAccess.DIRECT_ROOT -> root.execute(ComponentJournalCommand(context.packageName, replacement)).value
        WakeAccess.SHIZUKU_ROOT, WakeAccess.SHIZUKU_LEGACY_ROOT -> reader.withSession(access) { it.componentJournal(replacement) }
        else -> error("Root authorization required for component rollback")
    }
}

@Parcelize
private data class ComponentJournalCommand(val owner: String, val replacement: String?) : RootCommand<ParcelableString> {
    override suspend fun execute() = ParcelableString(componentJournalFile(owner, replacement))
}

internal fun componentJournalFile(owner: String, replacement: String?): String {
    if (replacement == null) return readComponentJournal(owner).orEmpty()
    writeComponentJournal(owner, replacement)
    return ""
}

/** The directory of the component journal: system-owned, so it lives as long as the settings it records. */
internal val COMPONENT_JOURNAL_DIRECTORY = File("/data/system")

/** At most this many bytes, so a journal always fits in one reply. */
internal const val MAX_JOURNAL_BYTES = 256 * 1024

/**
 * Checks that [owner] is a package name and that [contents] fits a journal.
 *
 * @throws IllegalArgumentException otherwise
 */
internal fun requireComponentJournal(owner: String, contents: String? = null) {
    require(owner.matches(Regex("""[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+"""))) { "Not a package name: $owner" }
    if (contents != null) {
        require(contents.toByteArray().size in 1..MAX_JOURNAL_BYTES) { "A component journal holds 1 to $MAX_JOURNAL_BYTES bytes" }
    }
}

/**
 * [owner]'s component journal in [directory], or null when there is none.
 *
 * @throws IllegalStateException when the file is empty or larger than [MAX_JOURNAL_BYTES]
 */
internal fun readComponentJournal(owner: String, directory: File = COMPONENT_JOURNAL_DIRECTORY): String? {
    requireComponentJournal(owner)
    val journal = File(directory, "blocker-wake-$owner.json")
    try {
        check(Os.stat(journal.path).st_size in 1..MAX_JOURNAL_BYTES.toLong()) { "Invalid component rollback journal size" }
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) return null
        throw e
    }
    return journal.readText()
}

/**
 * Replaces [owner]'s component journal in [directory] with [contents]: written to a file only
 * its owner may read or write, synced, renamed over the old one and the directory synced, so the
 * journal is the old or the new one after a crash.
 */
internal fun writeComponentJournal(owner: String, contents: String, directory: File = COMPONENT_JOURNAL_DIRECTORY) {
    requireComponentJournal(owner, contents)
    val journal = File(directory, "blocker-wake-$owner.json")
    val bytes = contents.toByteArray()
    val temporary = File.createTempFile(".blocker-wake-", ".tmp", directory)
    try {
        Os.chmod(temporary.path, 0x180)
        temporary.outputStream().use {
            it.write(bytes)
            it.fd.sync()
        }
        Os.rename(temporary.path, journal.path)
        val fd = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(fd)
        } finally {
            Os.close(fd)
        }
    } finally {
        temporary.delete()
    }
}
