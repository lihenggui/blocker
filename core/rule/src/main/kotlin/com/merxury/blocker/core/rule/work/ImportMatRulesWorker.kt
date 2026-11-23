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
import android.net.Uri
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.backup.MatRuleImporter
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_STORAGE_PERMISSION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Blocks the components a MAT export lists for one user through [MatRuleImporter], with the
 * controller type chosen when the work runs.
 */
@HiltWorker
class ImportMatRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val importer: MatRuleImporter,
    private val legacyCache: LegacyComponentCache,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_import_mat_rule_please_wait

    override suspend fun doWork(): Result {
        val lines = readLines(inputData.getString(PARAM_FILE_URI)) ?: return failureOf(MISSING_STORAGE_PERMISSION)
        val target = inputData.userOr(PARAM_TARGET_USER, ownUser())
        Timber.i("Import ${lines.size} MAT lines into user ${target.value}")
        val result = importer.import(
            lines = lines,
            target = target,
            includeSystemApps = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false),
        ) { current, done, total -> setForeground(updateNotification(current, done, total)) }
        Timber.i("Imported MAT rules: $result")
        legacyCache.refreshAfter(result, target)
        return result.toWorkResult(PARAM_IMPORT_COUNT) { it.applied }
    }

    /** The lines of the file at [uri], or null when it cannot be opened or read. */
    private suspend fun readLines(uri: String?): List<String>? = withContext(ioDispatcher) {
        if (uri == null) {
            Timber.e("No MAT file to import")
            return@withContext null
        }
        try {
            context.contentResolver.openInputStream(uri.toUri())?.use { it.reader().readLines() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the MAT file $uri")
            null
        }
    }

    companion object {
        /** Output: the components blocked. */
        const val PARAM_IMPORT_COUNT = "param_import_count"
        private const val PARAM_RESTORE_SYS_APPS = "param_restore_sys_apps"
        private const val PARAM_FILE_URI = "key_file_uri"

        /** Imports [fileUri] into [targetUser], by default the user Blocker runs in. */
        fun importWork(
            fileUri: Uri,
            restoreSystemApps: Boolean,
            targetUser: UserId? = null,
        ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ImportMatRulesWorker>()
            .setInputData(
                inputOf(
                    PARAM_FILE_URI to fileUri.toString(),
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    PARAM_TARGET_USER to targetUser?.value,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
