/*
 * Copyright 2023 Blocker
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

import android.content.ComponentName
import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.blocker.core.rule.EXTENSION
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.core.ifw.IIntentFirewall
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

@HiltWorker
class ExportBlockerRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val intentFirewall: IIntentFirewall,
    private val json: Json,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_backing_up_apps_please_wait

    override suspend fun doWork(): Result {
        // Check storage permission first
        val backupPath = inputData.getString(PARAM_FOLDER_PATH)?.let { File(it) }
        if (backupPath == null || !backupPath.isDirectory) {
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(backupPath)) {
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION),
            )
        }
        // Check backing up one application or all applications
        val packageName = inputData.getString(PARAM_BACKUP_APP_PACKAGE_NAME)
        if (!packageName.isNullOrEmpty()) {
            try {
                backupSingleApp(packageName, backupPath)
            } catch (e: Exception) {
                Timber.e(e, "Failed to export blocker rule for $packageName")
                return Result.failure(
                    workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
                )
            }
            return Result.success(workDataOf(PARAM_BACKUP_COUNT to 1))
        }
        // Notify users that work is being started
        Timber.i("Start to backup app rules")
        setForeground(updateNotification("", 0, 0))
        // Backup logic
        val shouldBackupSystemApp = inputData.getBoolean(PARAM_BACKUP_SYSTEM_APPS, false)
        return withContext(ioDispatcher) {
            var current = 1
            try {
                val list = if (shouldBackupSystemApp) {
                    ApplicationUtil.getApplicationList(context)
                } else {
                    ApplicationUtil.getThirdPartyApplicationList(context)
                }
                val total = list.count()
                list.forEach {
                    setForeground(updateNotification(it.packageName, current, total))
                    export(it.packageName, backupPath)
                    current++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export blocker rules")
                return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
                )
            }
            // Success, show a toast then cancel notifications
            Timber.i("Backup app rules finished.")
            return@withContext Result.success(
                workDataOf(PARAM_BACKUP_COUNT to current),
            )
        }
    }

    private suspend fun backupSingleApp(packageName: String, backupFolder: File) {
        Timber.d("Start to backup app rules for $packageName")
        setForeground(updateNotification(packageName, 1, 1))
        export(packageName, backupFolder)
    }

    private suspend fun export(packageName: String, backupFolder: File): Boolean {
        Timber.i("Export Blocker rules for $packageName")
        val pm = context.packageManager
        val applicationInfo = ApplicationUtil.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(
            packageName = applicationInfo.packageName,
            versionName = applicationInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(applicationInfo),
        )
        try {
            applicationInfo.receivers?.forEach {
                val stateIFW = intentFirewall.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        RECEIVER,
                        IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        RECEIVER,
                        PM,
                    ),
                )
            }
            applicationInfo.services?.forEach {
                val stateIFW = intentFirewall.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        SERVICE,
                        IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        SERVICE,
                        PM,
                    ),
                )
            }
            applicationInfo.activities?.forEach {
                val stateIFW = intentFirewall.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        ACTIVITY,
                        IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        ACTIVITY,
                        PM,
                    ),
                )
            }
            applicationInfo.providers?.forEach {
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        PROVIDER,
                        PM,
                    ),
                )
            }
            val result = if (rule.components.isNotEmpty()) {
                saveRuleToStorage(context, rule, packageName, backupFolder, ioDispatcher)
            } else {
                // No components exported, return true
                true
            }
            return result
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to export $packageName")
            return false
        }
    }

    private suspend fun saveRuleToStorage(
        context: Context,
        rule: BlockerRule,
        packageName: String,
        backupFolder: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean {
        val dir = DocumentFile.fromFile(backupFolder)
        // Create blocker rule file
        var file = dir.findFile(packageName + EXTENSION)
        if (file == null) {
            file = dir.createFile(BLOCKER_RULE_MIME, packageName)
        }
        if (file == null) {
            Timber.w("Cannot create rule $packageName")
            return false
        }
        return withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri, "rwt")?.use {
                    val text = json.encodeToString(rule)
                    it.write(text.toByteArray())
                }
                return@withContext true
            } catch (e: Exception) {
                Timber.e(e, "Cannot write rules for $packageName")
                return@withContext false
            }
        }
    }

    companion object {
        const val PARAM_BACKUP_COUNT = "param_backup_count"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_BACKUP_SYSTEM_APPS = "param_backup_system_apps"
        private const val PARAM_BACKUP_APP_PACKAGE_NAME = "param_backup_app_package_name"
        private const val BLOCKER_RULE_MIME = "application/json"

        fun exportWork(
            folderPath: String?,
            backupSystemApps: Boolean,
            backupPackageName: String? = null,
        ) =
            OneTimeWorkRequestBuilder<ExportBlockerRulesWorker>()
                .setInputData(
                    workDataOf(
                        PARAM_FOLDER_PATH to folderPath,
                        PARAM_BACKUP_SYSTEM_APPS to backupSystemApps,
                        PARAM_BACKUP_APP_PACKAGE_NAME to backupPackageName,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
    }
}
