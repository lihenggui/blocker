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

package com.merxury.blocker.core.domain

import android.content.Context
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.rule.work.CopyRulesToStorageWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class InitializeRuleStorageUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<InitializeState> = flow {
        val folder = filesDir.resolve(ruleBaseFolder)
        if (folder.exists() && folder.isDirectory && (folder.listFiles()?.isEmpty() == false)) {
            Timber.v("Rule storage already exists, skipping initialization")
            emit(InitializeState.Done)
            return@flow
        }
        val workManager = WorkManager.getInstance(appContext)
        Timber.v("Enqueue copy rules to storage worker")
        workManager.enqueueUniqueWork(
            CopyRulesToStorageWorker.WORK_NAME,
            KEEP,
            CopyRulesToStorageWorker.copyWork(),
        )
        workManager.getWorkInfosByTagFlow(CopyRulesToStorageWorker.WORK_NAME)
            .collect { workInfoList ->
                workInfoList.forEach { workInfo ->
                    Timber.d("WorkInfo: ${workInfo.tags}, state = ${workInfo.state}")
                    when (workInfo.state) {
                        State.SUCCEEDED -> emit(InitializeState.Done)
                        else -> emit(InitializeState.Initializing(workInfo.state.name))
                    }
                }
            }
    }.flowOn(ioDispatcher)
}
