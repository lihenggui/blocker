/*
 * Copyright 2024 Blocker
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
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.ShizukuControl
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_STORAGE_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

@HiltWorker
class ImportMatRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @IfwControl private val ifwController: IController,
    @RootApiControl private val rootController: IController,
    @ShizukuControl private val shizukuController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    private val uriString = params.inputData.getString(PARAM_FILE_URI)

    override fun getNotificationTitle(): Int = R.string.core_rule_import_mat_rule_please_wait

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        if (uri == null) {
            Timber.e("File URI is null, cannot import MAT rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_STORAGE_PERMISSION),
            )
        }
        val typeOrdinal = inputData.getInt(PARAM_CONTROLLER_TYPE, IFW.ordinal)
        val controller = when (ControllerType.entries[typeOrdinal]) {
            IFW -> ifwController
            PM -> rootController
            SHIZUKU -> shizukuController
        }
        val shouldRestoreSystemApps = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false)
        val uninstalledAppList = mutableListOf<String>()
        var total: Int
        var current = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val fileContent = stream.reader().readLines().filter { line ->
                    line.trim().isNotEmpty() && line.contains("/")
                }
                total = fileContent.count()
                fileContent.forEach { line ->
                    val trimmedLine = line.split(" ").firstOrNull() ?: return@forEach
                    val splitResult = trimmedLine.split("/")
                    val packageName = splitResult[0]
                    val name = splitResult[1]
                    if (isApplicationUninstalled(context, uninstalledAppList, packageName)) {
                        return@forEach
                    }
                    val isSystemApp = ApplicationUtil.isSystemApp(
                        context.packageManager,
                        packageName,
                    )
                    if (!shouldRestoreSystemApps && isSystemApp) {
                        return@forEach
                    }
                    setForeground(updateNotification(packageName, current, total))
                    val component = ComponentInfo(
                        packageName = packageName,
                        name = name,
                        // The controller doesn't care about the type of the component
                        // It will query internally, so we just set it to ACTIVITY
                        // Just to avoid compilation error
                        type = ACTIVITY,
                    )
                    controller.disable(component)
                    current++
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to import MAT files.")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e(e, "Error occurs while reading MAT files.")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        return@withContext Result.success(
            workDataOf(PARAM_IMPORT_COUNT to current),
        )
    }

    private fun isApplicationUninstalled(
        context: Context,
        savedList: MutableList<String>,
        packageName: String,
    ): Boolean {
        if (packageName.trim().isEmpty()) {
            return true
        }
        if (savedList.contains(packageName)) {
            return true
        }
        if (!ApplicationUtil.isAppInstalled(context.packageManager, packageName)) {
            savedList.add(packageName)
            return true
        }
        return false
    }

    companion object {
        const val PARAM_IMPORT_COUNT = "param_import_count"
        private const val PARAM_RESTORE_SYS_APPS = "param_restore_sys_apps"
        private const val PARAM_CONTROLLER_TYPE = "param_controller_type"
        private const val PARAM_FILE_URI = "key_file_uri"

        fun importWork(
            fileUri: Uri,
            controllerType: ControllerType,
            restoreSystemApps: Boolean,
        ): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<ImportMatRulesWorker>()
                .setInputData(
                    workDataOf(
                        PARAM_FILE_URI to fileUri.toString(),
                        PARAM_CONTROLLER_TYPE to controllerType.ordinal,
                        PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }
    }
}
