/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.rule.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.backup.BlockerRuleImporter
import com.merxury.blocker.core.rule.entity.RuleWorkType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber

/** Restores a set of Blocker rule backups into one user through [BlockerRuleImporter]. */
@HiltWorker
class ImportBlockerRuleWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val importer: BlockerRuleImporter,
    private val legacyCache: LegacyComponentCache,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_import_app_rules_please_wait

    override suspend fun doWork(): Result {
        val storage = when (val folder = openBackupFolder(context, inputData.getString(PARAM_FOLDER_PATH), ioDispatcher)) {
            is BackupFolder.Ready -> folder.storage
            is BackupFolder.Unusable -> return failureOf(folder.code)
        }
        val target = inputData.userOr(PARAM_TARGET_USER, ownUser())
        val source = inputData.sourceSetFor(target)
        Timber.i("Restore the Blocker rules of set '${source.folder}' into user ${target.value}")
        setForeground(updateNotification("", 0, 0))
        val result = importer.import(
            storage = storage,
            source = source,
            target = target,
            packageName = inputData.getString(PARAM_BACKUP_PACKAGE_NAME)?.takeIf { it.isNotEmpty() },
            includeSystemApps = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false),
        ) { current, done, total -> setForeground(updateNotification(current, done, total)) }
        Timber.i("Restored Blocker rules: $result")
        legacyCache.refreshAfter(result, target)
        return result.toWorkResult(PARAM_IMPORT_COUNT) { it.changedCount(RuleWorkType.IMPORT_BLOCKER_RULES) }
    }

    companion object {
        /** Output: the components whose state the restore changed. */
        const val PARAM_IMPORT_COUNT = "param_import_count"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_RESTORE_SYS_APPS = "param_restore_sys_apps"
        private const val PARAM_BACKUP_PACKAGE_NAME = "param_backup_package_name"

        /**
         * Restores [sourceSet] from [backupPath] into [targetUser], by default the user Blocker
         * runs in. The set defaults to the target user's own: the owner's for the owner, the
         * `user-<id>` one for another user.
         */
        fun importWork(
            backupPath: String?,
            restoreSystemApps: Boolean,
            backupPackageName: String? = null,
            targetUser: UserId? = null,
            sourceSet: BackupSet? = null,
        ) = OneTimeWorkRequestBuilder<ImportBlockerRuleWorker>()
            .setInputData(
                inputOf(
                    PARAM_FOLDER_PATH to backupPath,
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    PARAM_BACKUP_PACKAGE_NAME to backupPackageName,
                    PARAM_TARGET_USER to targetUser?.value,
                    PARAM_SOURCE_SET_USER to sourceSet?.user?.value,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
