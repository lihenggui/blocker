/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.privileged.calls

import android.os.Parcel
import android.os.Process
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isRegularAppUid
import com.merxury.blocker.core.privileged.PrivilegedAction
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import com.merxury.blocker.core.privileged.wake.WakeCommand
import com.merxury.blocker.core.privileged.wake.WakeJournalRead
import com.merxury.blocker.core.privileged.wake.readComponentJournal
import com.merxury.blocker.core.privileged.wake.requireComponentJournal
import com.merxury.blocker.core.privileged.wake.writeComponentJournal
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.isWakePackageName
import com.merxury.core.ifw.removeWakeRuleTarget
import com.merxury.core.ifw.wakeRuleFileName
import com.merxury.core.ifw.writeWakeRuleFile
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.io.ByteArrayOutputStream

// Root, the system uid and the shell: the uids a privileged process runs as.
private val PRIVILEGED_UIDS = setOf(0, 1000, 2000)

/*
 * The calls the wake feature makes. Each takes typed values only, never shell text or a path:
 * the command line comes from WakeCommand and the file names from the owner package, which each
 * call checks again when it is created in the privileged process.
 */

/**
 * The stdout of [command] for [user], run inside the privileged process, as root, the system uid
 * or the shell, without `su`. The process is killed after 12 seconds, output above 12 MiB fails
 * the call and stderr is dropped. [WakeCommand.INSTALL] reads its statsd config from stdin; the
 * config is built here from the id that [appUid] and [boot] give and is owned by the privileged
 * process's uid.
 *
 * The shell may run every command, so this does not need system files. [WakeCommand.DRAIN]
 * deletes the report it returns and is therefore not [retryable].
 *
 * @param appUid Blocker's uid, which with [boot] names Blocker's statsd config
 * @param boot the boot count, the other part of the config id
 * @param argument the resume point of [WakeCommand.EVENTS] in epoch milliseconds, or the index of
 * [WakeCommand.PROCSTATS_LAST]; ignored by the other commands
 * @param owner the uid the privileged process must run as: the statsd config owner the caller
 * expects. A process that runs as another uid, because the channel changed while a collection ran,
 * refuses the command, so a drain never deletes another owner's report.
 * @throws IllegalArgumentException when [appUid] is not an app's uid, [owner] is not root, the
 * system uid or the shell, or [user] or [argument] is out of range for [command]
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ReadWakeSource(
    val command: WakeCommand,
    val user: UserId,
    val appUid: Int,
    val boot: Int,
    val argument: Long = 0,
    val owner: Int,
) : PrivilegedCall<ByteArray> {
    init {
        require(isRegularAppUid(appUid)) { "Not an app uid: $appUid" }
        require(owner in PRIVILEGED_UIDS) { "Not a privileged uid: $owner" }
        processCommand()
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override val needsSystemFiles: Boolean get() = false

    override val retryable: Boolean get() = command != WakeCommand.DRAIN

    /** What the privileged process starts; it never goes through `su`. */
    fun processCommand(): List<String> = command.processCommand(user.value, appUid, boot, argument)

    override fun run(system: SystemServices): ByteArray {
        val uid = Process.myUid()
        check(uid == owner) { "The privileged process runs as uid $uid, not $owner" }
        val output = ByteArrayOutputStream()
        command.execute(user.value, appUid, boot, argument, output)
        return output.toByteArray()
    }

    override fun writeResult(result: ByteArray, parcel: Parcel) {
        parcel.writeByteArray(result)
    }

    override fun readResult(parcel: Parcel): ByteArray = checkNotNull(parcel.createByteArray())
}

/**
 * Replaces [owner]'s [kind] wake rule file in the Intent Firewall directory with [xml], so the
 * system only ever reads the old or the new file. Writing the content the file already holds
 * changes nothing. These files stay outside `IfwStore`, which leaves them out by design.
 *
 * @param owner the package name Blocker is installed under, which names the file
 */
@Parcelize
internal data class ReplaceWakeRuleFile(
    val owner: String,
    val kind: WakeRuleFileKind,
    val xml: String,
) : PrivilegedAction {
    init {
        wakeRuleFileName(owner, kind)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = writeWakeRuleFile(owner, kind, xml)

    // The content can be long and says nothing a log needs.
    override fun toString(): String = "ReplaceWakeRuleFile($owner, $kind, ${xml.length} chars)"
}

/** Deletes [owner]'s [kind] wake rule file; a missing file is already deleted. */
@Parcelize
internal data class DeleteWakeRuleFile(
    val owner: String,
    val kind: WakeRuleFileKind,
) : PrivilegedAction {
    init {
        wakeRuleFileName(owner, kind)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = writeWakeRuleFile(owner, kind, xml = null)
}

/**
 * Removes the rules for services of package [target] from [owner]'s [kind] wake rule file, and
 * the file once no rule is left. A missing file, or one without such rules, stays as it is.
 */
@Parcelize
internal data class RemoveWakeRuleTarget(
    val owner: String,
    val kind: WakeRuleFileKind,
    val target: String,
) : PrivilegedAction {
    init {
        wakeRuleFileName(owner, kind)
        require(isWakePackageName(target)) { "Not a package name: $target" }
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = removeWakeRuleTarget(owner, kind, target)
}

/**
 * The wake feature's component journal of [owner], `/data/system/blocker-wake-<owner>.json`: its
 * contents, that there is none, or that there is one the privileged process may not open.
 *
 * @throws IllegalStateException in the privileged process when the file is empty or larger than
 * 256 KiB, which becomes a system error
 */
@Parcelize
internal data class ReadWakeJournal(val owner: String) : PrivilegedCall<WakeJournalRead> {
    init {
        requireComponentJournal(owner)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): WakeJournalRead = readComponentJournal(owner)
}

/**
 * Replaces [owner]'s component journal with [contents]: a file of mode 0640 in the system group,
 * which root and the system uid can both read, renamed over the old one and the directory synced,
 * so after a crash it is the old or the new.
 *
 * @throws IllegalArgumentException when [contents] is empty or larger than 256 KiB
 */
@Parcelize
internal data class ReplaceWakeJournal(val owner: String, val contents: String) : PrivilegedAction {
    init {
        requireComponentJournal(owner, contents)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = writeComponentJournal(owner, contents)

    override fun toString(): String = "ReplaceWakeJournal($owner, ${contents.length} chars)"
}
