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

package com.merxury.blocker.core.data.respository.app

import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.database.app.InstalledAppDao
import com.merxury.blocker.core.database.app.asExternalModel
import com.merxury.blocker.core.database.app.fromExternalModel
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Error
import com.merxury.blocker.core.result.Result.Loading
import com.merxury.blocker.core.result.Result.Success
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import timber.log.Timber
import javax.inject.Inject

class LocalAppRepository @Inject constructor(
    private val localAppDataSource: LocalAppDataSource,
    private val componentRepository: LocalComponentRepository,
    private val installedAppDao: InstalledAppDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AppRepository {
    override fun getApplicationList(): Flow<List<InstalledApp>> =
        installedAppDao.getInstalledApps()
            .transform { list ->
                emit(list.map { it.asExternalModel() })
            }

    override fun updateApplication(packageName: String): Flow<Result<Unit>> = flow<Result<Unit>> {
        val app = localAppDataSource.getApplication(packageName).first()
        if (app == null) {
            // If we can't find the application, the application might be uninstalled
            installedAppDao.deleteByPackageName(packageName)
            componentRepository.deleteComponents(packageName)
        } else {
            installedAppDao.upsertInstalledApp(app.fromExternalModel())
            componentRepository.updateComponentList(app.packageName)
                .flowOn(ioDispatcher)
                .first()
        }
        emit(Success(Unit))
    }
        .onStart {
            Timber.v("Update app info: $packageName")
            emit(Loading)
        }
        .catch {
            Timber.e(it, "Error while updating app: $packageName")
            emit(Error(it))
        }

    override fun updateApplicationList(): Flow<Result<Unit>> = flow<Result<Unit>> {
        val cacheList = getApplicationList().first()
        val localList = localAppDataSource.getApplicationList().first()
        // Filter the uninstalled app first
        val uninstalledApp = cacheList.filter { cachedApp ->
            localList.find { installedApp ->
                installedApp.packageName == cachedApp.packageName
            } == null
        }
            .map { it.fromExternalModel() }
        if (uninstalledApp.isNotEmpty()) {
            Timber.d("Remove uninstalled in the cache. $uninstalledApp")
            installedAppDao.deleteApps(uninstalledApp)
            uninstalledApp.forEach {
                Timber.d("Remove components of ${it.packageName}")
                componentRepository.deleteComponents(it.packageName)
            }
        }
        // Update the latest app info from system
        val changedApps = localList.minus(cacheList.toSet())
            .map { it.fromExternalModel() }
        if (changedApps.isNotEmpty()) {
            Timber.d("${changedApps.size} apps changed")
            installedAppDao.upsertInstalledApps(changedApps)
            changedApps.forEach {
                componentRepository.updateComponentList(it.packageName)
                    .first()
            }
        }
        emit(Success(Unit))
    }
        .onStart {
            emit(Loading)
        }
        .catch { exception ->
            emit(Error(exception))
        }
        .flowOn(ioDispatcher)

    override fun searchInstalledApplications(keyword: String): Flow<List<InstalledApp>> {
        return installedAppDao.getByPackageNameOrLabelContains(keyword)
            .transform { list ->
                emit(list.map { it.asExternalModel() })
            }
    }

    override fun getApplication(packageName: String): Flow<InstalledApp?> {
        return installedAppDao.getInstalledApp(packageName)
            .map { it?.asExternalModel() }
    }
}
