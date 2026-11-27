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
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.backup.IfwRuleImporter
import com.merxury.blocker.core.rule.entity.RuleWorkType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber

/** Merges backed-up Intent Firewall rules into the device's rules through [IfwRuleImporter]. */
@HiltWorker
class ImportIfwRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val importer: IfwRuleImporter,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_import_ifw_please_wait

    override suspend fun doWork(): Result {
        val storage = when (val folder = openBackupFolder(context, inputData.getString(PARAM_FOLDER_PATH), ioDispatcher)) {
            is BackupFolder.Ready -> folder.storage
            is BackupFolder.Unusable -> return failureOf(folder.code)
        }
        Timber.i("Restore IFW rules")
        val result = importer.import(
            storage = storage,
            packageName = inputData.getString(PARAM_RESTORE_PACKAGE_NAME)?.takeIf { it.isNotEmpty() },
            includeSystemApps = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false),
        ) { current, done, total -> setForegroundSafely(updateNotification(current, done, total)) }
        Timber.i("Restored IFW rules: $result")
        // The rules apply to every user, so the list of the user Blocker runs in changes too.
        return result.toWorkResult(PARAM_IMPORT_COUNT) { it.changedCount(RuleWorkType.IMPORT_IFW_RULES) }
    }

    companion object {
        /** Output: the rules the restore added, each blocking components, and the files of a user's own it restored. */
        const val PARAM_IMPORT_COUNT = "param_import_count"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_RESTORE_SYS_APPS = "param_restore_sys_apps"
        private const val PARAM_RESTORE_PACKAGE_NAME = "param_restore_package_name"

        fun importIfwWork(
            backupPath: String?,
            restoreSystemApps: Boolean,
            packageName: String? = null,
        ) = OneTimeWorkRequestBuilder<ImportIfwRulesWorker>()
            .setInputData(
                inputOf(
                    PARAM_FOLDER_PATH to backupPath,
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    PARAM_RESTORE_PACKAGE_NAME to packageName,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
