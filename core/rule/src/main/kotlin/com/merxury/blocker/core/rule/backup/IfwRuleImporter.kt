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

import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.isRuleFileName
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlParseException
import com.merxury.core.ifw.xml.IfwXmlSerializer
import com.merxury.core.ifw.xml.requireIfwRulesDocument
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores Intent Firewall rule files from `ifw/<package>.xml` in the backup folder by merging
 * their whole rules into the rules on the device, as [IfwRuleMerge] does, in one change per
 * package through [IfwStore]: conditions and `block="false"` rules are kept, and a rule already
 * there is not added twice.
 *
 * A backup file named after an app installed for at least one user is merged into that app's
 * file, since the rules apply to every user. Any other file, whatever its name looks like, such as
 * a user's own `privacy.rules.xml` or the file of an app no longer installed, is restored whole,
 * byte for byte, only when no file of that name is on the device; it is never merged or
 * overwritten. A backup file to merge that the rule model cannot parse, and a file to restore whole
 * that is not a complete XML document with a `<rules>` root, are skipped and never written; a
 * damaged rule file on the device makes its package fail without being overwritten.
 *
 * A restore of one app reads only that app's file and merges only the rules that act on that app
 * alone: rules whose top-level component-filters all name it and that have no intent-filter. A
 * rule naming another app's component, or selecting by intent, would act on other apps, so it is
 * skipped as [SkipReason.OTHER_APP].
 */
class IfwRuleImporter @Inject constructor(
    private val ifwStore: IfwStore,
    private val deviceUsers: DeviceUserRepository,
    private val installedApps: InstalledAppRepository,
    private val channel: PrivilegedChannel,
    private val deserializer: IfwXmlDeserializer,
    serializer: IfwXmlSerializer,
) {
    private val merge = IfwRuleMerge(serializer)

    /**
     * Restores every backed-up file, merging those of installed apps, third-party apps only unless
     * [includeSystemApps], or only the rules of the installed app [packageName] when given.
     * Connects the privileged channel first, which may show the su or Shizuku prompt, and fails for
     * privilege when it cannot reach the rule directory or another user's apps.
     */
    suspend fun import(
        storage: BackupStorage,
        packageName: String? = null,
        includeSystemApps: Boolean = false,
        onProgress: RuleRunProgress = { _, _, _ -> },
    ): RuleRunResult {
        // Before the users are read: without the channel only the user Blocker runs in is seen.
        if (!channel.connectAllowing { it.canWriteSystemFiles }) return RuleRunResult.Failed(FailureReason.NO_PRIVILEGE)
        val files = try {
            storage.listFiles(IFW_BACKUP_FOLDER).filter { it.endsWith(IFW_RULE_EXTENSION) }.sorted()
        } catch (e: BackupStorageException) {
            Timber.e(e, "Cannot list the IFW backups")
            return RuleRunResult.Failed(FailureReason.STORAGE_UNAVAILABLE)
        }.filter { packageName == null || it == packageName + IFW_RULE_EXTENSION }
        val installed = when (val read = installedOnDevice()) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return RuleRunResult.Failed(read.reason.reason)
        }
        val summary = SummaryBuilder()
        files.forEachIndexed { index, file ->
            onProgress(file, index, files.size)
            val name = file.removeSuffix(IFW_RULE_EXTENSION)
            val isSystem = installed[name]
            when {
                !isRuleFileName(name) -> summary.skip(SkipReason.FILE_NOT_RESTORED)
                isSystem == null && packageName != null -> summary.skip(SkipReason.APP_NOT_INSTALLED)
                isSystem == null -> restoreWholeFile(storage, name, summary)
                isSystem && !includeSystemApps -> summary.skip(SkipReason.SYSTEM_APP)
                else -> importFile(storage, name, onlyThisApp = packageName != null, summary)
            }
        }
        return RuleRunResult.Completed(summary.build())
    }

    private suspend fun importFile(storage: BackupStorage, packageName: String, onlyThisApp: Boolean, summary: SummaryBuilder) {
        val read = readBackup(storage, packageName) ?: run {
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return
        }
        val backup = if (onlyThisApp) {
            val (own, other) = read.rules.partition { it.actsOnlyOn(packageName) }
            summary.skip(SkipReason.OTHER_APP, other.size)
            IfwRules(own)
        } else {
            read
        }
        val safe = IfwRules(backup.rules.mapNotNull(ifwStore::prepareRule))
        summary.skip(SkipReason.PROTECTED_COMPONENT, backup.rules.size - safe.rules.size)
        if (safe.rules.isEmpty()) return
        var merged: MergedIfwRules? = null
        when (val result = ifwStore.update(packageName) { current -> merge.merge(current, safe).also { merged = it }.rules }) {
            is IfwResult.Failure -> {
                Timber.w("Cannot restore the IFW rules of $packageName: ${result.reason}")
                result.reason.reason?.let { summary.fail(it) } ?: summary.skip(SkipReason.PROTECTED_COMPONENT)
            }

            is IfwResult.Success -> merged?.let {
                summary.applied += it.added
                summary.unchanged += it.present
                if (it.added > 0) {
                    summary.apps++
                    summary.changed(packageName)
                }
            }
        }
    }

    /**
     * Restores the backup `<name>.xml` of a file that is not an installed app's, only
     * when no file of that name is on the device and only when it is a well-formed XML document
     * whose root is `<rules>`; such a file is never merged or overwritten, and a truncated or
     * damaged one is never written. Its filters are not checked against Blocker's rule model,
     * since Android accepts some that the model cannot express, such as a matcher-less `<action />`.
     */
    private suspend fun restoreWholeFile(storage: BackupStorage, name: String, summary: SummaryBuilder) {
        val text = try {
            storage.read(ifwBackupPathOf(name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the IFW backup of $name")
            null
        } ?: run {
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return
        }
        if (text.isBlank()) {
            // Holds no rules, so there is nothing to restore.
            summary.unchanged++
            return
        }
        // A damaged file written now would keep a later restore of the app from changing it.
        try {
            requireIfwRulesDocument(text)
        } catch (e: IfwXmlParseException) {
            Timber.e(e, "The IFW backup of $name is not a rule file, leave it out")
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return
        }
        when (val result = ifwStore.restoreRuleFile(name, text)) {
            is IfwResult.Failure -> result.reason.reason?.let { summary.fail(it) } ?: summary.skip(SkipReason.PROTECTED_COMPONENT)

            is IfwResult.Success -> if (result.value) {
                summary.apps++
                summary.applied++
            } else {
                summary.skip(SkipReason.FILE_NOT_RESTORED)
            }
        }
    }

    /** The rules of [packageName]'s backup, or null when it cannot be read or parsed. */
    private suspend fun readBackup(storage: BackupStorage, packageName: String): IfwRules? {
        val path = ifwBackupPathOf(packageName)
        return try {
            val text = storage.read(path) ?: return null
            // An empty file holds no rules, as earlier versions read it.
            if (text.isBlank()) IfwRules.empty() else deserializer.deserialize(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the IFW backup $path")
            null
        }
    }

    /**
     * Whether each package installed for at least one user is a system app, by package name. Fails
     * when any user's apps cannot be read, since a package installed only there would be taken
     * for one that is not installed.
     */
    private suspend fun installedOnDevice(): ReadResult<Map<String, Boolean>> {
        val users = when (val read = deviceUsers.refresh()) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return read
        }
        val installed = mutableMapOf<String, Boolean>()
        for (user in users) {
            when (val read = installedApps.installedApps(user.id)) {
                is ReadResult.Success -> read.value.values.forEach { installed.merge(it.key.packageName, it.isSystem, Boolean::or) }

                is ReadResult.Failure -> {
                    Timber.w("Cannot read the apps of user ${user.id.value}: ${read.reason}")
                    return read
                }
            }
        }
        return ReadResult.Success(installed)
    }
}

/**
 * Whether this rule acts on [packageName]'s components alone: it has no intent-filter, which would
 * select intents for any app, and its top-level component-filters, of which it has one at least,
 * all name components of [packageName].
 */
internal fun IfwRule.actsOnlyOn(packageName: String): Boolean {
    if (intentFilters.isNotEmpty()) return false
    val named = filters.filterIsInstance<IfwFilter.ComponentFilter>()
    return named.isNotEmpty() && named.all { ComponentId.unflatten(it.name)?.packageName == packageName }
}
