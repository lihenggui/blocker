// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.ReadWakeJournal
import com.merxury.blocker.core.privileged.calls.ReplaceWakeJournal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.FileInputStream
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

    /**
     * The journal, or null when none has been written.
     *
     * @throws WakeJournalUnreadableException when there is a journal the channel's identity may not read
     */
    suspend fun read(): String? = when (val read = executor.executeIfReady(ReadWakeJournal(owner)).orThrow("read the component journal")) {
        WakeJournalRead.Missing -> null
        is WakeJournalRead.Found -> read.contents
        is WakeJournalRead.Unreadable -> throw WakeJournalUnreadableException("Cannot read the component journal: ${read.detail}")
    }

    suspend fun write(contents: String) {
        executor.executeIfReady(ReplaceWakeJournal(owner, contents)).orThrow("write the component journal")
    }
}

/** What reading the component journal found, in the privileged process. */
internal sealed interface WakeJournalRead : Parcelable {
    @Parcelize
    data object Missing : WakeJournalRead

    @Parcelize
    data class Found(val contents: String) : WakeJournalRead

    /** A journal is there but this identity may not open it; [detail] says whose it is. */
    @Parcelize
    data class Unreadable(val detail: String) : WakeJournalRead
}

/** The directory of the component journal: system-owned, so it lives as long as the settings it records. */
internal val COMPONENT_JOURNAL_DIRECTORY = File("/data/system")

/** At most this many bytes, so a journal always fits in one reply. */
internal const val MAX_JOURNAL_BYTES = 256 * 1024

// The group of the system uid. Both identities that may write the journal, root and the system
// uid, can then read it, so the user can switch between them without losing rollback data.
private const val SYSTEM_GID = 1000

// rw-r-----: the writer, and the system group, read it.
private const val JOURNAL_MODE = 0x1a0

private const val ROOT_UID = 0

private const val TAG = "WakeComponentJournal"

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
 * [owner]'s component journal in [directory]. One this process may not open is
 * [WakeJournalRead.Unreadable], such as a journal an earlier version wrote as root with mode 0600,
 * read as the system uid. Root gives a journal it reads the group and mode [writeComponentJournal]
 * sets, so a later read as the system uid succeeds.
 *
 * @throws IllegalStateException when the file is empty or larger than [MAX_JOURNAL_BYTES]
 */
// Runs in the privileged process, where no Timber tree is planted.
@SuppressLint("LogNotTimber")
internal fun readComponentJournal(owner: String, directory: File = COMPONENT_JOURNAL_DIRECTORY): WakeJournalRead {
    requireComponentJournal(owner)
    val path = File(directory, "blocker-wake-$owner.json").path
    val stat = try {
        Os.stat(path)
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) return WakeJournalRead.Missing
        throw e
    }
    check(stat.st_size in 1..MAX_JOURNAL_BYTES.toLong()) { "Invalid component rollback journal size" }
    val fd = try {
        Os.open(path, OsConstants.O_RDONLY, 0)
    } catch (e: ErrnoException) {
        if (e.errno != OsConstants.EACCES) throw e
        val mode = Integer.toOctalString(stat.st_mode and 0x1ff)
        return WakeJournalRead.Unreadable("owned by ${stat.st_uid}:${stat.st_gid} with mode $mode, read as uid ${Os.getuid()}")
    }
    val contents = try {
        FileInputStream(fd).readBytes().decodeToString()
    } finally {
        Os.close(fd)
    }
    if (Os.getuid() == ROOT_UID && (stat.st_gid != SYSTEM_GID || stat.st_mode and 0x1ff != JOURNAL_MODE)) {
        try {
            Os.chown(path, stat.st_uid, SYSTEM_GID)
            Os.chmod(path, JOURNAL_MODE)
        } catch (e: ErrnoException) {
            // The journal stays readable as root; the next write sets the group and mode again.
            Log.w(TAG, "Cannot share the component journal with the system uid", e)
        }
    }
    return WakeJournalRead.Found(contents)
}

/**
 * Replaces [owner]'s component journal in [directory] with [contents]: written to a file of
 * [group] with mode 0640, so root and the system uid can both read it, synced, renamed over the
 * old one and the directory synced, so the journal is the old or the new one after a crash.
 */
internal fun writeComponentJournal(owner: String, contents: String, directory: File = COMPONENT_JOURNAL_DIRECTORY, group: Int = SYSTEM_GID) {
    requireComponentJournal(owner, contents)
    val journal = File(directory, "blocker-wake-$owner.json")
    val bytes = contents.toByteArray()
    val temporary = File.createTempFile(".blocker-wake-", ".tmp", directory)
    try {
        Os.chmod(temporary.path, JOURNAL_MODE)
        Os.chown(temporary.path, -1, group)
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
