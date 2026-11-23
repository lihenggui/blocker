// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.ReadWakeJournal
import com.merxury.blocker.core.privileged.calls.ReplaceWakeJournal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Recovery metadata, kept with the same privileged lifetime as a PackageManager override. Read
 * and written through the privileged channel the user chose, only when it is ready: it never
 * connects, so it never shows a prompt, and throws when the call cannot be made.
 */
class WakeComponentJournal @Inject internal constructor(
    private val executor: PrivilegedExecutor,
    @param:ApplicationContext context: Context,
) {
    private val owner = context.packageName

    /** The journal, or null when none has been written. */
    suspend fun read(): String? = executor.executeIfReady(ReadWakeJournal(owner)).orThrow("read the component journal")

    suspend fun write(contents: String) {
        executor.executeIfReady(ReplaceWakeJournal(owner, contents)).orThrow("write the component journal")
    }
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
