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

package com.merxury.blocker.core.data.respository.userdata

import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.logAppSortingChanged
import com.merxury.blocker.core.data.respository.logBackupSystemAppPreferenceChanged
import com.merxury.blocker.core.data.respository.logComponentShowPriorityPreferenceChanged
import com.merxury.blocker.core.data.respository.logComponentSortingOrderPreferenceChanged
import com.merxury.blocker.core.data.respository.logComponentSortingPreferenceChanged
import com.merxury.blocker.core.data.respository.logControllerTypeChanged
import com.merxury.blocker.core.data.respository.logDarkThemeConfigChanged
import com.merxury.blocker.core.data.respository.logDynamicColorPreferenceChanged
import com.merxury.blocker.core.data.respository.logRestoreSystemAppPreferenceChanged
import com.merxury.blocker.core.data.respository.logRuleServerProviderChanged
import com.merxury.blocker.core.data.respository.logShowRunningAppsOnTopPreferenceChanged
import com.merxury.blocker.core.data.respository.logShowServiceInfoPreferenceChanged
import com.merxury.blocker.core.data.respository.logShowSystemAppPreferenceChanged
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSortingOrder
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalUserDataRepository @Inject constructor(
    private val blockerPreferenceDataSource: BlockerPreferencesDataSource,
    private val analyticsHelper: AnalyticsHelper,
) : UserDataRepository {
    override val userData: Flow<UserPreferenceData>
        get() = blockerPreferenceDataSource.userData

    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        blockerPreferenceDataSource.setDarkThemeConfig(darkThemeConfig)
        analyticsHelper.logDarkThemeConfigChanged(darkThemeConfig.name)
    }

    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        blockerPreferenceDataSource.setDynamicColorPreference(useDynamicColor)
        analyticsHelper.logDynamicColorPreferenceChanged(useDynamicColor)
    }

    override suspend fun setControllerType(controllerType: ControllerType) {
        blockerPreferenceDataSource.setControllerType(controllerType)
        analyticsHelper.logControllerTypeChanged(controllerType.name)
    }

    override suspend fun setRuleServerProvider(serverProvider: RuleServerProvider) {
        blockerPreferenceDataSource.setRuleServerProvider(serverProvider)
        analyticsHelper.logRuleServerProviderChanged(serverProvider.name)
    }

    override suspend fun setRuleBackupFolder(folder: String) =
        blockerPreferenceDataSource.setRuleBackupFolder(folder)

    override suspend fun setBackupSystemApp(shouldBackup: Boolean) {
        blockerPreferenceDataSource.setBackupSystemApp(shouldBackup)
        analyticsHelper.logBackupSystemAppPreferenceChanged(shouldBackup)
    }

    override suspend fun setRestoreSystemApp(shouldRestore: Boolean) {
        blockerPreferenceDataSource.setRestoreSystemApp(shouldRestore)
        analyticsHelper.logRestoreSystemAppPreferenceChanged(shouldRestore)
    }

    override suspend fun setShowSystemApps(shouldShowSystemApps: Boolean) {
        blockerPreferenceDataSource.setShowSystemApps(shouldShowSystemApps)
        analyticsHelper.logShowSystemAppPreferenceChanged(shouldShowSystemApps)
    }

    override suspend fun setShowServiceInfo(shouldShowServiceInfo: Boolean) {
        blockerPreferenceDataSource.setShowServiceInfo(shouldShowServiceInfo)
        analyticsHelper.logShowServiceInfoPreferenceChanged(shouldShowServiceInfo)
    }

    override suspend fun setAppSorting(sorting: AppSorting) {
        blockerPreferenceDataSource.setAppSorting(sorting)
        analyticsHelper.logAppSortingChanged(sorting.name)
    }

    override suspend fun setComponentShowPriority(priority: ComponentShowPriority) {
        blockerPreferenceDataSource.setComponentShowPriority(priority)
        analyticsHelper.logComponentShowPriorityPreferenceChanged(priority.name)
    }

    override suspend fun setComponentSorting(sorting: ComponentSorting) {
        blockerPreferenceDataSource.setComponentSorting(sorting)
        analyticsHelper.logComponentSortingPreferenceChanged(sorting.name)
    }

    override suspend fun setComponentSortingOrder(order: ComponentSortingOrder) {
        blockerPreferenceDataSource.setComponentSortingOrder(order)
        analyticsHelper.logComponentSortingOrderPreferenceChanged(order.name)
    }

    override suspend fun setShowRunningAppsOnTop(shouldShowRunningAppsOnTop: Boolean) {
        blockerPreferenceDataSource.setShowRunningAppsOnTop(shouldShowRunningAppsOnTop)
        analyticsHelper.logShowRunningAppsOnTopPreferenceChanged(shouldShowRunningAppsOnTop)
    }
}
