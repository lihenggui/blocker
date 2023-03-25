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

import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    val userData: Flow<UserPreferenceData>

    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig)

    /**
     * Sets the preferred dynamic color config.
     */
    suspend fun setDynamicColorPreference(useDynamicColor: Boolean)

    suspend fun setControllerType(controllerType: ControllerType)

    suspend fun setRuleServerProvider(serverProvider: RuleServerProvider)

    suspend fun setRuleBackupFolder(folder: String)

    suspend fun setBackupSystemApp(shouldBackup: Boolean)

    suspend fun setRestoreSystemApp(shouldRestore: Boolean)

    suspend fun setShowSystemApps(shouldShowSystemApps: Boolean)

    suspend fun setShowServiceInfo(shouldShowServiceInfo: Boolean)

    suspend fun setAppSorting(sorting: AppSorting)

    suspend fun setComponentShowPriority(priority: ComponentShowPriority)

    suspend fun setShowRunningAppsOnTop(shouldShowRunningAppsOnTop: Boolean)
}
