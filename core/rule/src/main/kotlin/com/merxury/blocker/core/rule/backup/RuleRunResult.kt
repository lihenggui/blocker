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

package com.merxury.blocker.core.rule.backup

import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.system.ReadFailure

/** How a backup, restore, import or reset ended. */
sealed interface RuleRunResult {
    /** The run went through its items; some may still have been skipped or have failed. */
    data class Completed(val summary: RuleRunSummary) : RuleRunResult

    /** The run could not start on its items at all, for [reason]. */
    data class Failed(val reason: FailureReason) : RuleRunResult
}

/** Why an item was left alone on purpose. Each reason says what it counts. */
enum class SkipReason {
    /**
     * The app is not installed for the user, or for any user for Intent Firewall rules. Counts
     * components in a Blocker rule import, a MAT import and a reset, files in an Intent Firewall import.
     */
    APP_NOT_INSTALLED,

    /** A system app while system apps are left out. Counts apps in a backup, files in an Intent Firewall import, components otherwise. */
    SYSTEM_APP,

    /** The app declares no component, so it has nothing to back up. Counts apps. */
    NO_COMPONENTS,

    /** An entry names a component the installed app does not declare. Counts entries or components. */
    NOT_DECLARED,

    /** A MAT line that is not `package/class`. Counts lines. */
    MALFORMED_LINE,

    /**
     * A backed-up rule file that is not an installed app's, such as a user's own `my-rules.xml`,
     * and is not restored because a file of that name is on the device, which is never
     * overwritten, or because Blocker may not write a file of that name. Counts files.
     */
    FILE_NOT_RESTORED,

    /**
     * A file, entry or rule that would act on another app than the one it is restored for: a
     * Blocker rule file or entry naming another package than the file's, or in a restore of one
     * app, an Intent Firewall rule naming another app's component or selecting by intent. Counts
     * components, one per entry, in a Blocker rule import, rules in an Intent Firewall import.
     */
    OTHER_APP,
}

/** Why something could not be done. Each reason says what it counts. */
enum class FailureReason {
    /** The backup folder cannot be reached. Only for [RuleRunResult.Failed]. */
    STORAGE_UNAVAILABLE,

    /**
     * A backup file cannot be read or parsed, names a value this version does not know, or an
     * existing backup this run would have to merge with cannot be. Counts files.
     */
    BACKUP_UNREADABLE,

    /** The app's Intent Firewall rule file on the device cannot be read, so it is left untouched. Counts apps, or entries of a restore. */
    RULES_UNREADABLE,

    /** No root or Shizuku, or the identity it runs as may not do this. Counts entries, or apps. */
    NO_PRIVILEGE,

    /** The app's state could not be read from the system. Counts apps. */
    STATE_UNREADABLE,

    /** The pre-update list of PackageManager blocks could not be recovered. Only for [RuleRunResult.Failed]. */
    LEGACY_PM_RECORDS_UNREADABLE,

    /** Writing a backup file or a rule file failed. Counts files. */
    WRITE_FAILED,

    /** The system failed in another way, or the change did not show afterwards. Counts entries. */
    SYSTEM_ERROR,
}

/**
 * What a run did.
 *
 * A backup counts files written in [apps]; every run that changes the system counts in [applied]:
 * components a Blocker rule import, a MAT import or a reset changed, rules an Intent Firewall import
 * added. Skips and failures count in the unit their reason names.
 *
 * @property apps apps whose backup file was written, or whose state or rules were changed, and
 * rule files of a user's own that were restored
 * @property applied entries written to backups, or components changed, or rules added
 * @property unchanged entries or rules that already were as the backup says
 * @property skipped items left alone on purpose, by reason
 * @property failed items that could not be done, by reason
 * @property firewallUnavailable the Intent Firewall rules could not be reached at all, so a
 * backup left them out or kept those of the file it replaced
 * @property changedPackages packages whose state or rules this run changed
 */
data class RuleRunSummary(
    val apps: Int = 0,
    val applied: Int = 0,
    val unchanged: Int = 0,
    val skipped: Map<SkipReason, Int> = emptyMap(),
    val failed: Map<FailureReason, Int> = emptyMap(),
    val firewallUnavailable: Boolean = false,
    val changedPackages: Set<String> = emptySet(),
) {
    val skippedCount: Int get() = skipped.values.sum()
    val failedCount: Int get() = failed.values.sum()
}

/** Collects a [RuleRunSummary] while a run goes through its items. */
internal class SummaryBuilder {
    var apps = 0
    var applied = 0
    var unchanged = 0
    var firewallUnavailable = false
    private val skipped = mutableMapOf<SkipReason, Int>()
    private val failed = mutableMapOf<FailureReason, Int>()
    private val changed = mutableSetOf<String>()

    fun skip(reason: SkipReason, count: Int = 1) {
        if (count > 0) skipped.merge(reason, count, Int::plus)
    }

    fun fail(reason: FailureReason, count: Int = 1) {
        if (count > 0) failed.merge(reason, count, Int::plus)
    }

    fun changed(packageName: String) {
        changed += packageName
    }

    fun build() = RuleRunSummary(apps, applied, unchanged, skipped.toMap(), failed.toMap(), firewallUnavailable, changed.toSet())
}

/** The failure category of a read of the system. */
internal val ReadFailure.reason: FailureReason
    get() = when (this) {
        is ReadFailure.UserNotReachable, ReadFailure.ChannelUnavailable -> FailureReason.NO_PRIVILEGE
        is ReadFailure.RulesUnreadable -> FailureReason.RULES_UNREADABLE
        is ReadFailure.PackageNotFound, is ReadFailure.ManifestUnreadable -> FailureReason.STATE_UNREADABLE
    }

/** The failure category of one layer of a control request. */
internal val ControlFailure.reason: FailureReason
    get() = when (this) {
        ControlFailure.ChannelUnavailable -> FailureReason.NO_PRIVILEGE
        is ControlFailure.NotPermitted -> if (isSystemRefusal) FailureReason.SYSTEM_ERROR else FailureReason.NO_PRIVILEGE
        ControlFailure.RulesUnreadable -> FailureReason.RULES_UNREADABLE
        is ControlFailure.WriteFailed -> FailureReason.WRITE_FAILED
        is ControlFailure.TargetNotFound, is ControlFailure.SystemError, ControlFailure.StateNotConfirmed -> FailureReason.SYSTEM_ERROR
    }

/** The failure category of a rule store call. */
internal val IfwFailure.reason: FailureReason
    get() = when (this) {
        IfwFailure.NoAccess -> FailureReason.NO_PRIVILEGE
        is IfwFailure.Unreadable -> FailureReason.RULES_UNREADABLE
        is IfwFailure.WriteFailed -> FailureReason.WRITE_FAILED
    }
