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
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.isPackageRuleFileName
import com.merxury.core.ifw.isRuleFileName
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores Intent Firewall rule files from `ifw/<package>.xml` in the backup folder by merging
 * their whole rules into the rules on the device, as [IfwRuleMerge] does, in one change per
 * package through [IfwStore]: conditions and `block="false"` rules are kept, and a rule already
 * there is not added twice.
 *
 * A backup file that cannot be parsed is skipped; a damaged rule file on the device makes its
 * package fail without being overwritten. Only packages installed for at least one user are
 * restored, since the rules apply to every user. A backed-up file not named after a package, a
 * user's own such as `my-rules.xml`, is restored byte for byte only when no file of that name is
 * on the device, and only when no single package was asked for.
 */
class IfwRuleImporter @Inject constructor(
    private val ifwStore: IfwStore,
    private val deviceUsers: DeviceUserRepository,
    private val installedApps: InstalledAppRepository,
    private val deserializer: IfwXmlDeserializer,
    serializer: IfwXmlSerializer,
) {
    private val merge = IfwRuleMerge(serializer)

    /**
     * Restores every backed-up file whose package is installed, third-party packages only unless
     * [includeSystemApps], or only [packageName]'s file when given. Acquires the privileged
     * channel when needed.
     */
    suspend fun import(
        storage: BackupStorage,
        packageName: String? = null,
        includeSystemApps: Boolean = false,
        onProgress: RuleRunProgress = { _, _, _ -> },
    ): RuleRunResult {
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
                !isPackageRuleFileName(name) -> restoreOwnFile(storage, name, summary)
                isSystem == null -> summary.skip(SkipReason.APP_NOT_INSTALLED)
                isSystem && !includeSystemApps -> summary.skip(SkipReason.SYSTEM_APP)
                else -> importFile(storage, name, summary)
            }
        }
        return RuleRunResult.Completed(summary.build())
    }

    private suspend fun importFile(storage: BackupStorage, packageName: String, summary: SummaryBuilder) {
        val backup = readBackup(storage, packageName) ?: run {
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return
        }
        var merged: MergedIfwRules? = null
        when (val result = ifwStore.update(packageName) { current -> merge.merge(current, backup).also { merged = it }.rules }) {
            is IfwResult.Failure -> {
                Timber.w("Cannot restore the IFW rules of $packageName: ${result.reason}")
                summary.fail(result.reason.reason)
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
     * Restores the backup of a user's own rule file `<name>.xml` byte for byte, only when no file
     * of that name is on the device; such a file is never merged or overwritten.
     */
    private suspend fun restoreOwnFile(storage: BackupStorage, name: String, summary: SummaryBuilder) {
        if (!isRuleFileName(name)) {
            summary.skip(SkipReason.FILE_NOT_RESTORED)
            return
        }
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
        when (val result = ifwStore.restoreRuleFile(name, text)) {
            is IfwResult.Failure -> summary.fail(result.reason.reason)

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
     * Whether each package installed for at least one user that can be read is a system app,
     * by package name. Fails only when no user's apps can be read.
     */
    private suspend fun installedOnDevice(): ReadResult<Map<String, Boolean>> {
        val users = when (val read = deviceUsers.refresh()) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return read
        }
        val installed = mutableMapOf<String, Boolean>()
        var failure: ReadResult.Failure? = null
        users.forEach { user ->
            when (val read = installedApps.installedApps(user.id)) {
                is ReadResult.Success -> read.value.values.forEach { installed.merge(it.key.packageName, it.isSystem, Boolean::or) }

                is ReadResult.Failure -> {
                    Timber.w("Cannot read the apps of user ${user.id.value}: ${read.reason}")
                    failure = read
                }
            }
        }
        return failure?.takeIf { installed.isEmpty() && users.isNotEmpty() } ?: ReadResult.Success(installed)
    }
}
