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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull

val emptyUserData = UserPreferenceData(
    darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    useDynamicColor = false,
    controllerType = ControllerType.IFW,
    ruleServerProvider = RuleServerProvider.GITHUB,
    ruleBackupFolder = "",
    backupSystemApp = false,
    restoreSystemApp = false,
    showSystemApps = false,
    showServiceInfo = false,
    appSorting = AppSorting.NAME,
    appSortingOrder = SortingOrder.ASCENDING,
    componentShowPriority = ComponentShowPriority.ENABLED_COMPONENTS_FIRST,
    componentSortingOrder = SortingOrder.ASCENDING,
    componentSorting = COMPONENT_NAME,
    isFirstTimeInitializationCompleted = false,
    showRunningAppsOnTop = false,
)
class TestUserDataRepository : UserDataRepository {

    /**
     * The backing hot flow for the list of UserPreferenceData for testing.
     */
    private val _userData = MutableSharedFlow<UserPreferenceData>(replay = 1, onBufferOverflow = DROP_OLDEST)
    private val currentUserData get() = _userData.replayCache.firstOrNull() ?: emptyUserData

    override val userData: Flow<UserPreferenceData> = _userData.filterNotNull()

    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        _userData.tryEmit(currentUserData.copy(darkThemeConfig = darkThemeConfig))
    }

    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        _userData.tryEmit(currentUserData.copy(useDynamicColor = useDynamicColor))
    }

    override suspend fun setControllerType(controllerType: ControllerType) {
        _userData.tryEmit(currentUserData.copy(controllerType = controllerType))
    }

    override suspend fun setRuleServerProvider(serverProvider: RuleServerProvider) {
        _userData.tryEmit(currentUserData.copy(ruleServerProvider = serverProvider))
    }

    override suspend fun setRuleBackupFolder(folder: String) {
        _userData.tryEmit(currentUserData.copy(ruleBackupFolder = folder))
    }

    override suspend fun setBackupSystemApp(shouldBackup: Boolean) {
        _userData.tryEmit(currentUserData.copy(backupSystemApp = shouldBackup))
    }

    override suspend fun setRestoreSystemApp(shouldRestore: Boolean) {
        _userData.tryEmit(currentUserData.copy(restoreSystemApp = shouldRestore))
    }

    override suspend fun setShowSystemApps(shouldShowSystemApps: Boolean) {
        _userData.tryEmit(currentUserData.copy(showSystemApps = shouldShowSystemApps))
    }

    override suspend fun setShowServiceInfo(shouldShowServiceInfo: Boolean) {
        _userData.tryEmit(currentUserData.copy(showServiceInfo = shouldShowServiceInfo))
    }

    override suspend fun setAppSorting(sorting: AppSorting) {
        _userData.tryEmit(currentUserData.copy(appSorting = sorting))
    }

    override suspend fun setAppSortingOrder(order: SortingOrder) {
        _userData.tryEmit(currentUserData.copy(appSortingOrder = order))
    }

    override suspend fun setComponentShowPriority(priority: ComponentShowPriority) {
        _userData.tryEmit(currentUserData.copy(componentShowPriority = priority))
    }

    override suspend fun setComponentSortingOrder(order: SortingOrder) {
        _userData.tryEmit(currentUserData.copy(componentSortingOrder = order))
    }

    override suspend fun setComponentSorting(sorting: ComponentSorting) {
        _userData.tryEmit(currentUserData.copy(componentSorting = sorting))
    }

    override suspend fun setShowRunningAppsOnTop(shouldShowRunningAppsOnTop: Boolean) {
        _userData.tryEmit(currentUserData.copy(showRunningAppsOnTop = shouldShowRunningAppsOnTop))
    }

    override suspend fun setIsFirstTimeInitializationCompleted(completed: Boolean) {
        _userData.tryEmit(currentUserData.copy(isFirstTimeInitializationCompleted = completed))
    }

    fun sendUserData(userData: UserPreferenceData) {
        _userData.tryEmit(userData)
    }
}
