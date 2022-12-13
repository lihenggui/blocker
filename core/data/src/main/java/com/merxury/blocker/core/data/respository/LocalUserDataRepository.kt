/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.data.respository

import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.ThemeBrand
import com.merxury.blocker.core.model.preference.UserPreferenceData
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LocalUserDataRepository @Inject constructor(
    private val blockerPreferenceDataSource: BlockerPreferencesDataSource
) : UserDataRepository {
    override val userData: Flow<UserPreferenceData>
        get() = blockerPreferenceDataSource.userData

    override suspend fun setThemeBrand(themeBrand: ThemeBrand) =
        blockerPreferenceDataSource.setThemeBrand(themeBrand)

    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) =
        blockerPreferenceDataSource.setDarkThemeConfig(darkThemeConfig)

    override suspend fun setControllerType(controllerType: ControllerType) =
        blockerPreferenceDataSource.setControllerType(controllerType)

    override suspend fun setRuleServerProvider(serverProvider: RuleServerProvider) =
        blockerPreferenceDataSource.setRuleServerProvider(serverProvider)

    override suspend fun setRuleBackupFolder(folder: String) =
        blockerPreferenceDataSource.setRuleBackupFolder(folder)

    override suspend fun setBackupSystemApp(shouldBackup: Boolean) =
        blockerPreferenceDataSource.setBackupSystemApp(shouldBackup)

    override suspend fun setRestoreSystemApp(shouldRestore: Boolean) =
        blockerPreferenceDataSource.setRestoreSystemApp(shouldRestore)

    override suspend fun setShowSystemApps(shouldShowSystemApps: Boolean) =
        blockerPreferenceDataSource.setShowSystemApps(shouldShowSystemApps)

    override suspend fun setShowServiceInfo(shouldShowServiceInfo: Boolean) =
        blockerPreferenceDataSource.setShowServiceInfo(shouldShowServiceInfo)

    override suspend fun setAppSorting(sorting: AppSorting) =
        blockerPreferenceDataSource.setAppSorting(sorting)

    override suspend fun setComponentShowPriority(priority: ComponentShowPriority) =
        blockerPreferenceDataSource.setComponentShowPriority(priority)
}
