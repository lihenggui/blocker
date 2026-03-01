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

package com.merxury.blocker.core.domain.applist

import android.content.pm.PackageManager
import com.merxury.blocker.core.data.appstate.IAppStateCache
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME
import com.merxury.blocker.core.model.preference.AppSorting.NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

/**
 * A use case which returns the application list.
 * The invoke method accepts a query string and returns a flow of application list.
 */

class SearchAppListUseCase @Inject constructor(
    private val pm: PackageManager,
    private val userDataRepository: UserDataRepository,
    private val appRepository: AppRepository,
    private val appStateCache: IAppStateCache,
    private val getAppController: GetAppControllerUseCase,
    private val getServiceController: GetServiceControllerUseCase,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(query: String): Flow<List<AppItem>> = combineTransform(
        userDataRepository.userData,
        appRepository.getApplicationList(),
        getAppController(),
        getServiceController(),
    ) { userData, appList, appController, serviceController ->
        // Prepare the application data
        Timber.v("App list updated, keywords: $query")
        appController.refreshRunningAppList()
        if (userData.showServiceInfo) {
            serviceController.load()
        }
        Timber.v("Controllers are initialized")
        val sortType = userData.appSorting
        val sortOrder = userData.appSortingOrder
        val finalList = if (userData.showSystemApps) {
            appList
        } else {
            appList.filterNot { it.isSystem }
        }.filter {
            it.label.contains(query, true) ||
                it.packageName.contains(query, true)
        }.map { installedApp ->
            val packageName = installedApp.packageName
            val cachedServiceStatus = if (userData.showServiceInfo) {
                appStateCache.getOrNull(packageName)
            } else {
                null
            }
            AppItem(
                label = installedApp.label,
                packageName = packageName,
                versionName = installedApp.versionName,
                versionCode = installedApp.versionCode,
                isSystem = installedApp.isSystem,
                isRunning = appController.isAppRunning(packageName),
                isEnabled = installedApp.isEnabled,
                firstInstallTime = installedApp.firstInstallTime,
                lastUpdateTime = installedApp.lastUpdateTime,
                appServiceStatus = cachedServiceStatus,
                packageInfo = pm.getPackageInfoCompat(packageName, 0),
            )
        }.sortedWith(
            appComparator(sortType, sortOrder),
        ).let { sortedList ->
            when (userData.topAppType) {
                TopAppType.NONE -> sortedList
                TopAppType.RUNNING -> sortedList.sortedByDescending { it.isRunning }
                TopAppType.DISABLED -> sortedList.sortedByDescending { !it.isEnabled }
            }
        }
        emit(finalList)
        // Load service status is not a cheap operation,
        // so we only load it when user wants to see it
        if (userData.showServiceInfo) {
            val listWithServiceInfo = finalList.map {
                val serviceStatus = appStateCache.get(it.packageName)
                it.copy(appServiceStatus = serviceStatus)
            }
            emit(listWithServiceInfo)
        }
    }
        .flowOn(cpuDispatcher)

    private fun appComparator(sortType: AppSorting, sortOrder: SortingOrder): Comparator<AppItem> = if (sortOrder == SortingOrder.ASCENDING) {
        when (sortType) {
            NAME -> compareBy { it.label.lowercase() }
            FIRST_INSTALL_TIME -> compareBy { it.firstInstallTime }
            LAST_UPDATE_TIME -> compareBy { it.lastUpdateTime }
        }
    } else {
        when (sortType) {
            NAME -> compareByDescending { it.label.lowercase() }
            FIRST_INSTALL_TIME -> compareByDescending { it.firstInstallTime }
            LAST_UPDATE_TIME -> compareByDescending { it.lastUpdateTime }
        }
    }
}
