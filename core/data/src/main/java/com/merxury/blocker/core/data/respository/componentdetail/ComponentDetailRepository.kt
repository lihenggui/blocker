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

package com.merxury.blocker.core.data.respository.componentdetail

import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.changeListSync
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.data.respository.componentdetail.datasource.LocalComponentDetailDataSource
import com.merxury.blocker.core.data.respository.componentdetail.datasource.UserGeneratedComponentDetailDataSource
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.datastore.ChangeListVersions
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.io.BinaryFileWriter
import com.merxury.blocker.core.network.model.NetworkChangeList
import com.merxury.blocker.core.utils.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val RULE_FOLDER_NAME = "rules"
private const val RULE_ZIP_FILENAME = "rules.zip"
private const val COMMIT_INFO_FILE = "commit_info.txt"

class ComponentDetailRepository @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val localComponentDetailRepository: LocalComponentDetailDataSource,
    private val userGeneratedDataSource: UserGeneratedComponentDetailDataSource,
    private val network: BlockerNetworkDataSource,
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IComponentDetailRepository {

    override fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?> =
        userGeneratedDataSource.getComponentDetail(name)

    override fun getLocalComponentDetail(name: String): Flow<ComponentDetail?> = flow {
        // Priority: user generated > db
        val userGeneratedData = userGeneratedDataSource.getComponentDetail(name)
            .first()
        if (userGeneratedData != null) {
            emit(userGeneratedData)
            return@flow
        }
        val localData = localComponentDetailRepository.getComponentDetail(name)
            .first()
        if (localData != null) {
            emit(localData)
            return@flow
        }
        emit(null)
    }
        .flowOn(ioDispatcher)

    override fun saveComponentDetail(componentDetail: ComponentDetail): Flow<Boolean> =
        userGeneratedDataSource.saveComponentData(componentDetail)

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean {
        Timber.d("Syncing component detail")
        val provider = userDataRepository.userData.first().ruleServerProvider
        return synchronizer.changeListSync(
            versionReader = ChangeListVersions::ruleCommitId,
            changeFetcher = {
                val commitId = network.getRuleLatestCommitId(provider)
                    .ruleCommitId
                NetworkChangeList(commitId)
            },
            versionUpdater = { latestVersion ->
                copy(ruleCommitId = latestVersion)
            },
            modelUpdater = { commitId ->
                withContext(ioDispatcher) {
                    val ruleFolder = filesDir.resolve(RULE_FOLDER_NAME)
                    if (!ruleFolder.exists()) {
                        ruleFolder.mkdirs()
                    }
                    val file = File(ruleFolder, RULE_ZIP_FILENAME)
                    file.outputStream().use { outputStream ->
                        network.downloadRules(provider, BinaryFileWriter(outputStream))
                    }
                    // unzip the folder to rule folder
                    FileUtils.unzip(file, ruleFolder.absolutePath)
                    // write commit id to file
                    val commitInfoFile = File(ruleFolder, COMMIT_INFO_FILE)
                    commitInfoFile.writeText(commitId)
                }
            },
        )
    }
}
