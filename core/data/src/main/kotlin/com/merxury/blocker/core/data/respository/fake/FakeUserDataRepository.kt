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

package com.merxury.blocker.core.data.respository.fake

import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FakeUserDataRepository @Inject constructor(
    private val blockerPreferenceDataSource: BlockerPreferencesDataSource,
) : UserDataRepository {
    override val userData: Flow<UserPreferenceData> =
        blockerPreferenceDataSource.userData
    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        blockerPreferenceDataSource.setDarkThemeConfig(darkThemeConfig)
    }

    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        blockerPreferenceDataSource.setDynamicColorPreference(useDynamicColor)
    }

    override suspend fun setControllerType(controllerType: ControllerType) {
        blockerPreferenceDataSource.setControllerType(controllerType)
    }

    override suspend fun setRuleServerProvider(serverProvider: RuleServerProvider) {
        blockerPreferenceDataSource.setRuleServerProvider(serverProvider)
    }

    override suspend fun setRuleBackupFolder(folder: String) {
        blockerPreferenceDataSource.setRuleBackupFolder(folder)
    }

    override suspend fun setBackupSystemApp(shouldBackup: Boolean) {
        blockerPreferenceDataSource.setBackupSystemApp(shouldBackup)
    }

    override suspend fun setRestoreSystemApp(shouldRestore: Boolean) {
        blockerPreferenceDataSource.setRestoreSystemApp(shouldRestore)
    }

    override suspend fun setShowSystemApps(shouldShowSystemApps: Boolean) {
        blockerPreferenceDataSource.setShowSystemApps(shouldShowSystemApps)
    }

    override suspend fun setShowServiceInfo(shouldShowServiceInfo: Boolean) {
        blockerPreferenceDataSource.setShowServiceInfo(shouldShowServiceInfo)
    }

    override suspend fun setAppSorting(sorting: AppSorting) {
        blockerPreferenceDataSource.setAppSorting(sorting)
    }

    override suspend fun setAppSortingOrder(order: SortingOrder) {
        blockerPreferenceDataSource.setAppSortingOrder(order)
    }

    override suspend fun setComponentShowPriority(priority: ComponentShowPriority) {
        blockerPreferenceDataSource.setComponentShowPriority(priority)
    }

    override suspend fun setComponentSortingOrder(order: SortingOrder) {
        blockerPreferenceDataSource.setComponentSortingOrder(order)
    }

    override suspend fun setComponentSorting(sorting: ComponentSorting) {
        blockerPreferenceDataSource.setComponentSorting(sorting)
    }

    override suspend fun setShowRunningAppsOnTop(shouldShowRunningAppsOnTop: Boolean) {
        blockerPreferenceDataSource.setShowRunningAppsOnTop(shouldShowRunningAppsOnTop)
    }

    override suspend fun setIsFirstTimeInitializationCompleted(completed: Boolean) {
        blockerPreferenceDataSource.setIsFirstTimeInitializationCompleted(completed)
    }

    override suspend fun setAppDisplayLanguage(language: String) {
        blockerPreferenceDataSource.setAppDisplayLanguage(language)
    }

    override suspend fun setRuleDisplayLanguage(language: String) {
        blockerPreferenceDataSource.setRuleDisplayLanguage(language)
    }
}
