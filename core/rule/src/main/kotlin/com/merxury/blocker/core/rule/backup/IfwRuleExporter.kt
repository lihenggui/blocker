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

import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.isPackageRuleFileName
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/** The folder in a backup folder that holds Intent Firewall rule files, one set for the device. */
internal const val IFW_BACKUP_FOLDER = "ifw"

/** The extension of a backed-up Intent Firewall rule file, `ifw/<package>.xml`. */
internal const val IFW_RULE_EXTENSION = ".xml"

/** The backup path of [packageName]'s Intent Firewall rule file. */
internal fun ifwBackupPathOf(packageName: String): String = pathOf(IFW_BACKUP_FOLDER, packageName + IFW_RULE_EXTENSION)

/**
 * Copies the device's Intent Firewall rule files into the backup folder as `ifw/<package>.xml`,
 * byte for byte as stored, a damaged file included, and a user's own files such as
 * `ifw/my-rules.xml` too. Rules apply to every user, so there is one set; the wake feature's own
 * files are not copied.
 */
class IfwRuleExporter @Inject constructor(
    private val ifwStore: IfwStore,
    private val channel: PrivilegedChannel,
) {
    /**
     * Copies every rule file, or only [packageName]'s when given; a package without a file is
     * left out. Connects the privileged channel first, which may show the su or Shizuku prompt,
     * and fails for privilege when it cannot reach the rule directory.
     */
    suspend fun export(
        storage: BackupStorage,
        packageName: String? = null,
        onProgress: RuleRunProgress = { _, _, _ -> },
    ): RuleRunResult {
        if (!channel.connectAllowing { it.canWriteSystemFiles }) return RuleRunResult.Failed(FailureReason.NO_PRIVILEGE)
        val packages = if (packageName != null) {
            listOf(packageName).filter(::isPackageRuleFileName)
        } else {
            when (val all = ifwStore.ruleFileNames()) {
                is IfwResult.Success -> all.value.sorted()
                is IfwResult.Failure -> return RuleRunResult.Failed(all.reason.reason ?: FailureReason.STATE_UNREADABLE)
            }
        }
        val summary = SummaryBuilder()
        packages.forEachIndexed { index, name ->
            onProgress(name + IFW_RULE_EXTENSION, index, packages.size)
            val text = when (val read = ifwStore.ruleFileText(name)) {
                is IfwResult.Success -> read.value ?: return@forEachIndexed

                is IfwResult.Failure -> {
                    summary.fail(read.reason.reason ?: FailureReason.STATE_UNREADABLE)
                    return@forEachIndexed
                }
            }
            try {
                storage.write(ifwBackupPathOf(name), text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot write the IFW backup of $name")
                summary.fail(FailureReason.WRITE_FAILED)
                return@forEachIndexed
            }
            summary.apps++
            summary.applied++
        }
        return RuleRunResult.Completed(summary.build())
    }
}
