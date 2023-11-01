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

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.rule.EXTENSION
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.util.StorageUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@HiltWorker
class ListAllComponentsToStorageWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val json: Json,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {
    override fun getNotificationTitle(): Int = R.string.core_rule_backing_up_apps_please_wait

    override suspend fun doWork(): Result {
        // Check storage permission first
        val backupPath = inputData.getString(PARAM_FOLDER_PATH)
        if (backupPath.isNullOrEmpty()) {
            return Result.failure(
                workDataOf(RuleWorkResult.PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, backupPath)) {
            return Result.failure(
                workDataOf(RuleWorkResult.PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION),
            )
        }
        Timber.d("Start to list all components to $backupPath")
        setForeground(updateNotification("Exporting data", 1, 1))
        val applications = appRepository.getApplicationList().first()
        val appComponentInfoList = applications.map { application ->
            val packageName = application.packageName
            val components = componentRepository.getComponentList(packageName).first()
            val receiver = components.filter { it.type == RECEIVER }
                .map { Component(it.name) }
            val activity = components.filter { it.type == ACTIVITY }
                .map { Component(it.name) }
            val service = components.filter { it.type == SERVICE }
                .map { Component(it.name) }
            val provider = components.filter { it.type == PROVIDER }
                .map { Component(it.name) }
            AppComponentInfo(
                packageName = packageName,
                receiver = receiver,
                activity = activity,
                service = service,
                provider = provider,
            )
        }
        saveRuleToStorage(context, appComponentInfoList, Uri.parse(backupPath), ioDispatcher)
        Timber.d("All components are saved to $backupPath")
        return Result.success()
    }

    private suspend fun saveRuleToStorage(
        context: Context,
        components: List<AppComponentInfo>,
        destUri: Uri,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean {
        val dir = DocumentFile.fromTreeUri(context, destUri)
        if (dir == null) {
            Timber.e("Cannot open $destUri")
            return false
        }
        // Create blocker rule file
        val fileName = "components_info_collection$EXTENSION"
        var file = dir.findFile(fileName)
        if (file == null) {
            file = dir.createFile("application/json", fileName)
        }
        if (file == null) {
            Timber.w("Cannot create rule $fileName")
            return false
        }
        return withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri, "rwt")?.use {
                    val text = json.encodeToString(components)
                    it.write(text.toByteArray())
                }
                return@withContext true
            } catch (e: Exception) {
                Timber.e(e, "Cannot write all components to $fileName")
                return@withContext false
            }
        }
    }

    companion object {
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        fun listAppComponentsWork(folderPath: String?) =
            OneTimeWorkRequestBuilder<ListAllComponentsToStorageWorker>()
                .setInputData(
                    workDataOf(
                        PARAM_FOLDER_PATH to folderPath,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
    }
}

@Serializable
private data class AppComponentInfo(
    val packageName: String,
    val receiver: List<Component>,
    val activity: List<Component>,
    val service: List<Component>,
    val provider: List<Component>,
)

@Serializable
private data class Component(
    val name: String,
)
