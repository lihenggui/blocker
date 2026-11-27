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
import com.merxury.blocker.core.rule.backup.BlockerRuleExporter
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.utils.currentUserId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber

/** Backs up Blocker rules of one user, or of one app, through [BlockerRuleExporter]. */
@HiltWorker
class ExportBlockerRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val exporter: BlockerRuleExporter,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_backing_up_apps_please_wait

    override suspend fun doWork(): Result {
        val storage = when (val folder = openBackupFolder(context, inputData.getString(PARAM_FOLDER_PATH), ioDispatcher)) {
            is BackupFolder.Ready -> folder.storage
            is BackupFolder.Unusable -> return failureOf(folder.code)
        }
        val user = inputData.userOr(PARAM_TARGET_USER, currentUserId())
        Timber.i("Back up Blocker rules of user ${user.value}")
        setForegroundSafely(updateNotification("", 0, 0))
        val result = exporter.export(
            storage = storage,
            user = user,
            packageName = inputData.getString(PARAM_BACKUP_APP_PACKAGE_NAME)?.takeIf { it.isNotEmpty() },
            includeSystemApps = inputData.getBoolean(PARAM_BACKUP_SYSTEM_APPS, false),
        ) { current, done, total -> setForegroundSafely(updateNotification(current, done, total)) }
        Timber.i("Backed up Blocker rules: $result")
        return result.toWorkResult(PARAM_BACKUP_COUNT) { it.changedCount(RuleWorkType.EXPORT_BLOCKER_RULES) }
    }

    companion object {
        /** Output: the backup files written. */
        const val PARAM_BACKUP_COUNT = "param_backup_count"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_BACKUP_SYSTEM_APPS = "param_backup_system_apps"
        private const val PARAM_BACKUP_APP_PACKAGE_NAME = "param_backup_app_package_name"

        /** Backs up [user]'s apps, by default the user Blocker runs in, into [folderPath]. */
        fun exportWork(
            folderPath: String?,
            backupSystemApps: Boolean,
            backupPackageName: String? = null,
            user: UserId? = null,
        ) = OneTimeWorkRequestBuilder<ExportBlockerRulesWorker>()
            .setInputData(
                inputOf(
                    PARAM_FOLDER_PATH to folderPath,
                    PARAM_BACKUP_SYSTEM_APPS to backupSystemApps,
                    PARAM_BACKUP_APP_PACKAGE_NAME to backupPackageName,
                    PARAM_TARGET_USER to user?.value,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
