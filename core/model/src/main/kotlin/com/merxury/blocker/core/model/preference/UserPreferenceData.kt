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

package com.merxury.blocker.core.model.preference

import com.merxury.blocker.core.model.data.AppSortInfo
import com.merxury.blocker.core.model.data.ComponentSortInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.UserEditableSettings

data class UserPreferenceData(
    val darkThemeConfig: DarkThemeConfig,
    val useDynamicColor: Boolean,
    val controllerType: ControllerType,
    val ruleServerProvider: RuleServerProvider,
    val ruleBackupFolder: String,
    val backupSystemApp: Boolean,
    val restoreSystemApp: Boolean,
    val showSystemApps: Boolean,
    val showServiceInfo: Boolean,
    val appSorting: AppSorting,
    val appSortingOrder: SortingOrder,
    val componentSorting: ComponentSorting,
    val componentSortingOrder: SortingOrder,
    val componentShowPriority: ComponentShowPriority,
    val showRunningAppsOnTop: Boolean,
    val isFirstTimeInitializationCompleted: Boolean,
    val appDisplayLanguage: String,
    val libDisplayLanguage: String,
) {
    fun toUserEditableSettings() = UserEditableSettings(
        controllerType = controllerType,
        ruleServerProvider = ruleServerProvider,
        appDisplayLanguage = appDisplayLanguage,
        libDisplayLanguage = libDisplayLanguage,
        ruleBackupFolder = ruleBackupFolder,
        backupSystemApp = backupSystemApp,
        restoreSystemApp = restoreSystemApp,
        showSystemApps = showSystemApps,
        showServiceInfo = showServiceInfo,
        darkThemeConfig = darkThemeConfig,
        useDynamicColor = useDynamicColor,
    )

    fun toAppSortInfo() = AppSortInfo(
        sorting = appSorting,
        order = appSortingOrder,
        showRunningAppsOnTop = showRunningAppsOnTop,
    )

    fun toComponentSortInfo() = ComponentSortInfo(
        sorting = componentSorting,
        order = componentSortingOrder,
        priority = componentShowPriority,
    )
}
