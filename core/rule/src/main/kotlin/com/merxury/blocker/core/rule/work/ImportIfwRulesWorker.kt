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
import androidx.work.workDataOf
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.rule.IFW_EXTENSION
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult.FOLDER_NOT_DEFINED
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_STORAGE_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.core.ifw.Rules
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import nl.adaptivity.xmlutil.serialization.XML
import timber.log.Timber
import java.io.IOException

@HiltWorker
class ImportIfwRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val xmlParser: XML,
    @IfwControl private val ifwController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_import_ifw_please_wait

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val folderPath = inputData.getString(PARAM_FOLDER_PATH)
        if (folderPath.isNullOrEmpty()) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, folderPath)) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_STORAGE_PERMISSION),
            )
        }
        Timber.i("Started to import IFW rules")
        val total: Int
        var importedCount = 0
        try {
            val shouldRestoreSystemApps = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false)
            // Check directory is readable
            val ifwFolder = StorageUtil.getOrCreateIfwFolder(context, folderPath)
                ?: return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
                )
            val files = ifwFolder.listFiles()
                .filter { it.isFile && it.name?.endsWith(".xml") == true }
            total = files.count()
            // Start importing files
            files.forEach { documentFile ->
                val restoredPackage = inputData.getString(PARAM_RESTORE_PACKAGE_NAME)
                if (!restoredPackage.isNullOrEmpty()) {
                    // Import 1 IFW file case
                    // It will follow the 'Restore system app' setting
                    if (documentFile.name != restoredPackage + IFW_EXTENSION) {
                        return@forEach
                    }
                }
                Timber.i("Importing ${documentFile.name}")
                setForeground(updateNotification(documentFile.name ?: "", importedCount, total))
                context.contentResolver.openInputStream(documentFile.uri)?.use { stream ->
                    val fileContent = stream.bufferedReader().use { it.readText() }
                    if (fileContent.isEmpty()) {
                        return@forEach
                    }
                    val rule = Rules.decodeFromString(xmlParser, fileContent)
                    val activities = rule.activity.componentFilter
                        .map { getComponentFromName(it.name) }
                    val broadcast = rule.broadcast.componentFilter
                        .map { getComponentFromName(it.name) }
                    val service = rule.service.componentFilter
                        .map { getComponentFromName(it.name) }
                    // Assume that all components in the same file belong to the same package
                    val packageName = activities.firstOrNull()?.packageName
                        ?: broadcast.firstOrNull()?.packageName
                        ?: service.firstOrNull()?.packageName
                        ?: return@forEach
                    val isSystemApp =
                        ApplicationUtil.isSystemApp(context.packageManager, packageName)
                    if (!shouldRestoreSystemApps && isSystemApp) {
                        Timber.i("Skipping system app $packageName")
                        return@forEach
                    }
                    ifwController.batchDisable(activities) {}
                    ifwController.batchDisable(broadcast) {}
                    ifwController.batchDisable(service) {}
                    importedCount++
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Cannot import IFW rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e(e, "Cannot read IFW rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Imported $importedCount IFW rules.")
        return@withContext Result.success(
            workDataOf(PARAM_IMPORT_COUNT to importedCount),
        )
    }

    private fun getComponentFromName(name: String): ComponentInfo {
        val splitResult = name.split("/")
        val packageName = splitResult[0]
        val componentName = splitResult[1]
        return ComponentInfo(
            packageName = packageName,
            name = componentName,
            type = ACTIVITY,
        )
    }

    companion object {
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
                workDataOf(
                    PARAM_FOLDER_PATH to backupPath,
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    PARAM_RESTORE_PACKAGE_NAME to packageName,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
