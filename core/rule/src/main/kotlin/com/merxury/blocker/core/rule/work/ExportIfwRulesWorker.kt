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
import com.merxury.blocker.core.rule.backup.IfwRuleExporter
import com.merxury.blocker.core.rule.entity.RuleWorkType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber

/** Copies the device's Intent Firewall rule files into the backup folder through [IfwRuleExporter]. */
@HiltWorker
class ExportIfwRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val exporter: IfwRuleExporter,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {
    override fun getNotificationTitle(): Int = R.string.core_rule_backing_up_ifw_please_wait

    override suspend fun doWork(): Result {
        val storage = when (val folder = openBackupFolder(context, inputData.getString(PARAM_FOLDER_PATH), ioDispatcher)) {
            is BackupFolder.Ready -> folder.storage
            is BackupFolder.Unusable -> return failureOf(folder.code)
        }
        Timber.i("Back up IFW rules")
        val result = exporter.export(
            storage = storage,
            packageName = inputData.getString(PARAM_BACKUP_PACKAGE_NAME)?.takeIf { it.isNotEmpty() },
        ) { current, done, total -> setForeground(updateNotification(current, done, total)) }
        Timber.i("Backed up IFW rules: $result")
        return result.toWorkResult(PARAM_EXPORT_COUNT) { it.changedCount(RuleWorkType.EXPORT_IFW_RULES) }
    }

    companion object {
        /** Output: the rule files copied. */
        const val PARAM_EXPORT_COUNT = "param_export_count"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_BACKUP_PACKAGE_NAME = "param_backup_package_name"

        fun exportWork(folderPath: String?, backupPackageName: String? = null) = OneTimeWorkRequestBuilder<ExportIfwRulesWorker>()
            .setInputData(
                inputOf(
                    PARAM_FOLDER_PATH to folderPath,
                    PARAM_BACKUP_PACKAGE_NAME to backupPackageName,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
