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

package com.merxury.blocker.core.domain

import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.rule.work.CopyRulesToStorageWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class InitializeRuleStorageUseCase @Inject constructor(
    private val workManager: WorkManager,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    private val userDataRepository: UserDataRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<InitializeState> = flow {
        // Check if there is existing rule storage
        val serverProvider = userDataRepository.userData.first().ruleServerProvider
        val repoName = serverProvider.projectName
        val repoBase = filesDir.resolve(filesDir).resolve(repoName)
        val repoGitFolder = repoBase.resolve(".git")
        if (repoBase.exists() && repoGitFolder.exists()) {
            Timber.v("Rule storage exists, skipping initialization")
            emit(InitializeState.Done)
            return@flow
        }
        // Check if ruleBaseFolder exists
        val folder = filesDir.resolve(ruleBaseFolder)
        val isValidDir = folder.exists() && folder.isDirectory
        val containRule = if (isValidDir) {
            // list all files that filename equals to 'general.json'
            (folder.listFiles()?.any { it.name == "general.json" }) == true
        } else {
            false
        }
        if (containRule) {
            Timber.v("Internal storage already exists, skipping initialization")
            emit(InitializeState.Done)
            return@flow
        }
        // Rule storage does not exist, enqueue copy assets to storage worker
        Timber.v("Enqueue copy rules to storage worker")
        emit(InitializeState.Initializing(""))
        workManager.enqueueUniqueWork(
            CopyRulesToStorageWorker.WORK_NAME,
            KEEP,
            CopyRulesToStorageWorker.copyWork(),
        )
        workManager.getWorkInfosByTagFlow(CopyRulesToStorageWorker.WORK_NAME)
            .collect { workInfoList ->
                val unfinishedWork = workInfoList
                    .filter { it.state in listOf(State.ENQUEUED, State.RUNNING) }
                if (unfinishedWork.isNotEmpty()) {
                    Timber.v("Unfinished work: $unfinishedWork")
                    return@collect
                }
                emit(InitializeState.Done)
            }
    }
        .flowOn(ioDispatcher)
}
