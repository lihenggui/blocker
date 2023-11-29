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
import android.content.pm.PackageManager
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.command.RootController
import com.merxury.blocker.core.controllers.shizuku.ShizukuController
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.rule.EXTENSION
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber

@HiltWorker
class ImportBlockerRuleWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val pm: PackageManager,
    private val rootController: RootController,
    private val ifwController: IfwController,
    private val shizukuController: ShizukuController,
    private val json: Json,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_import_app_rules_please_wait

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        // Check storage permission first
        val backupPath = inputData.getString(PARAM_FOLDER_PATH)
        if (backupPath.isNullOrEmpty()) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, backupPath)) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION),
            )
        }
        val controllerOrdinal =
            inputData.getInt(PARAM_CONTROLLER_TYPE, IFW.ordinal)
        val controllerType = ControllerType.entries[controllerOrdinal]
        val packageManager = context.packageManager
        val backupPackageName = inputData.getString(PARAM_BACKUP_PACKAGE_NAME)
        val shouldRestoreSystemApp = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false)
        val documentDir = DocumentFile.fromTreeUri(context, Uri.parse(backupPath))
        if (documentDir == null) {
            Timber.e("Cannot create DocumentFile")
            return@withContext Result.failure()
        }
        if (!backupPackageName.isNullOrEmpty()) {
            return@withContext importSingleRule(
                packageManager,
                documentDir,
                backupPackageName,
                controllerType,
            )
        }
        Timber.i("Start to import app rules")
        var successCount = 0
        try {
            val files = documentDir.listFiles()
                .filter { it.name?.endsWith(EXTENSION) == true }
            val total = files.count()
            var current = 1
            files.forEach {
                Timber.i("Import ${it.uri}")
                context.contentResolver.openInputStream(it.uri)?.use { input ->
                    val rule = json.decodeFromStream<BlockerRule>(input)
                    val appInstalled =
                        ApplicationUtil.isAppInstalled(packageManager, rule.packageName)
                    val isSystemApp = ApplicationUtil.isSystemApp(packageManager, rule.packageName)
                    if (!appInstalled) {
                        Timber.w("App ${rule.packageName} is not installed, skipping")
                        current++
                        return@forEach
                    }
                    if (!shouldRestoreSystemApp && isSystemApp) {
                        Timber.d("App ${rule.packageName} is a system app, skipping")
                        current++
                        return@forEach
                    }
                    setForeground(updateNotification(rule.packageName ?: "", current, total))
                    val restoredComponentCount = import(rule, controllerType)
                    if (restoredComponentCount > 0) {
                        successCount++
                    }
                    current++
                }
            }
            Timber.i("Import rules finished.")
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to import blocker rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
            )
        }
        Timber.i("Imported $successCount rules.")
        return@withContext Result.success(
            workDataOf(PARAM_IMPORT_COUNT to successCount),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun importSingleRule(
        pm: PackageManager,
        documentDir: DocumentFile,
        packageName: String,
        controllerType: ControllerType,
    ): Result {
        val appInstalled = ApplicationUtil.isAppInstalled(pm, packageName)
        if (!appInstalled) {
            Timber.w("App $packageName is not installed, skipping")
            return Result.failure()
        }
        try {
            val files = documentDir.listFiles()
                .filter { it.name?.endsWith(EXTENSION) == true }
            files.forEach {
                Timber.i("Import ${it.uri}")
                context.contentResolver.openInputStream(it.uri)?.use { input ->
                    val rule = json.decodeFromStream<BlockerRule>(input)
                    if (rule.packageName != packageName) {
                        return@forEach
                    }
                    setForeground(updateNotification(rule.packageName ?: "", 1, 1))
                    import(rule, controllerType)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to import blocker rules for $packageName")
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
            )
        }
        return Result.success(
            workDataOf(PARAM_IMPORT_COUNT to 1),
        )
    }

    private suspend fun import(rule: BlockerRule, type: ControllerType): Int {
        val fallbackController = if (type == PM) {
            rootController
        } else {
            shizukuController
        }
        var count = 0
        rule.components.forEach {
            if (it.method == IFW) {
                if (it.type == PROVIDER) {
                    // IFW controller did not support disabling provider
                    // Fallback to other controller
                    if (!it.state) {
                        fallbackController.enable(it.packageName, it.name)
                    } else {
                        fallbackController.disable(it.packageName, it.name)
                    }
                } else {
                    if (!it.state) {
                        ifwController.enable(it.packageName, it.name)
                    } else {
                        ifwController.disable(it.packageName, it.name)
                    }
                }
                count++
            } else {
                // For PM controllers, state enabled means component is enabled
                val currentState = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                if (currentState == it.state) return@forEach
                if (it.state) {
                    fallbackController.enable(it.packageName, it.name)
                } else {
                    fallbackController.disable(it.packageName, it.name)
                }
                count++
            }
        }
        return count
    }

    companion object {
        const val PARAM_IMPORT_COUNT = "param_import_count"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_RESTORE_SYS_APPS = "param_restore_sys_apps"
        private const val PARAM_CONTROLLER_TYPE = "param_controller_type"
        private const val PARAM_BACKUP_PACKAGE_NAME = "param_backup_package_name"

        fun importWork(
            backupPath: String?,
            restoreSystemApps: Boolean,
            controllerType: ControllerType,
            backupPackageName: String? = null,
        ) = OneTimeWorkRequestBuilder<ImportBlockerRuleWorker>()
            .setInputData(
                workDataOf(
                    PARAM_FOLDER_PATH to backupPath,
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    PARAM_CONTROLLER_TYPE to controllerType.ordinal,
                    PARAM_BACKUP_PACKAGE_NAME to backupPackageName,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
