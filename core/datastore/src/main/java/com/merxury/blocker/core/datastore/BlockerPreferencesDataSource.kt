/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.datastore

import androidx.datastore.core.DataStore
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BlockerPreferencesDataSource @Inject constructor(
    private val userPreferences: DataStore<UserPreferences>,
) {

    val userData = userPreferences.data.map {
        UserPreferenceData(
            darkThemeConfig = when (it.darkThemeConfig) {
                null,
                DarkThemeConfigProto.DARK_THEME_CONFIG_UNSPECIFIED,
                DarkThemeConfigProto.UNRECOGNIZED,
                DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM,
                ->
                    DarkThemeConfig.FOLLOW_SYSTEM

                DarkThemeConfigProto.DARK_THEME_CONFIG_LIGHT -> DarkThemeConfig.LIGHT
                DarkThemeConfigProto.DARK_THEME_CONFIG_DARK -> DarkThemeConfig.DARK
            },
            controllerType = when (it.controllerType) {
                null,
                ControllerTypeProto.UNRECOGNIZED,
                ControllerTypeProto.IFW,
                -> ControllerType.IFW

                ControllerTypeProto.PM -> ControllerType.PM
                ControllerTypeProto.SHIZUKU -> ControllerType.SHIZUKU
            },
            ruleServerProvider = when (it.ruleServerProvider) {
                null,
                RuleServerProviderProto.UNRECOGNIZED,
                RuleServerProviderProto.GITLAB,
                -> RuleServerProvider.GITLAB

                RuleServerProviderProto.GITHUB -> RuleServerProvider.GITHUB
            },
            ruleBackupFolder = it.ruleBackupFolder,
            backupSystemApp = it.backupSystemApp,
            restoreSystemApp = it.restoreSystemApp,
            showSystemApps = it.showSystemApps,
            showServiceInfo = it.showServiceInfo,
            appSorting = when (it.appSorting) {
                null,
                AppSortingProto.UNRECOGNIZED,
                AppSortingProto.APP_NAME_ASCENDING,
                ->
                    AppSorting.NAME_ASCENDING

                AppSortingProto.APP_NAME_DESCENDING ->
                    AppSorting.NAME_DESCENDING

                AppSortingProto.FIRST_INSTALL_TIME_ASCENDING ->
                    AppSorting.FIRST_INSTALL_TIME_ASCENDING

                AppSortingProto.FIRST_INSTALL_TIME_DESCENDING ->
                    AppSorting.FIRST_INSTALL_TIME_DESCENDING

                AppSortingProto.LAST_UPDATE_TIME_ASCENDING ->
                    AppSorting.LAST_UPDATE_TIME_ASCENDING

                AppSortingProto.LAST_UPDATE_TIME_DESCENDING ->
                    AppSorting.LAST_UPDATE_TIME_DESCENDING
            },
            componentSorting = when (it.componentSorting) {
                null,
                ComponentSortingProto.UNRECOGNIZED,
                ComponentSortingProto.COMPONENT_NAME_ASCENDING,
                ->
                    ComponentSorting.NAME_ASCENDING

                ComponentSortingProto.COMPONENT_NAME_DESCENDING ->
                    ComponentSorting.NAME_DESCENDING
            },
            componentShowPriority = when (it.componentShowPriority) {
                null,
                ComponentShowPriorityProto.UNRECOGNIZED,
                ComponentShowPriorityProto.ENABLED_COMPONENTS_FIRST,
                ->
                    ComponentShowPriority.ENABLED_COMPONENTS_FIRST

                ComponentShowPriorityProto.DISABLED_COMPONENTS_FIRST ->
                    ComponentShowPriority.DISABLED_COMPONENTS_FIRST
            },
            useDynamicColor = it.useDynamicColor,
            showRunningAppsOnTop = it.showRunningAppsOnTop,
        )
    }

    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        userPreferences.updateData {
            it.copy {
                this.darkThemeConfig = when (darkThemeConfig) {
                    DarkThemeConfig.FOLLOW_SYSTEM ->
                        DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM

                    DarkThemeConfig.LIGHT -> DarkThemeConfigProto.DARK_THEME_CONFIG_LIGHT
                    DarkThemeConfig.DARK -> DarkThemeConfigProto.DARK_THEME_CONFIG_DARK
                }
            }
        }
    }

    suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        userPreferences.updateData {
            it.copy {
                this.useDynamicColor = useDynamicColor
            }
        }
    }

    suspend fun setControllerType(controllerType: ControllerType) {
        userPreferences.updateData {
            it.copy {
                this.controllerType = when (controllerType) {
                    ControllerType.IFW -> ControllerTypeProto.IFW
                    ControllerType.PM -> ControllerTypeProto.PM
                    ControllerType.SHIZUKU -> ControllerTypeProto.SHIZUKU
                }
            }
        }
    }

    suspend fun setRuleServerProvider(serverProvider: RuleServerProvider) {
        userPreferences.updateData {
            it.copy {
                this.ruleServerProvider = when (serverProvider) {
                    RuleServerProvider.GITHUB -> RuleServerProviderProto.GITHUB
                    RuleServerProvider.GITLAB -> RuleServerProviderProto.GITLAB
                }
            }
        }
    }

    suspend fun setRuleBackupFolder(folder: String) {
        userPreferences.updateData {
            it.copy { this.ruleBackupFolder = folder }
        }
    }

    suspend fun setBackupSystemApp(shouldBackup: Boolean) {
        userPreferences.updateData {
            it.copy { this.backupSystemApp = shouldBackup }
        }
    }

    suspend fun setRestoreSystemApp(shouldRestore: Boolean) {
        userPreferences.updateData {
            it.copy { this.restoreSystemApp = shouldRestore }
        }
    }

    suspend fun setShowSystemApps(shouldShowSystemApps: Boolean) {
        userPreferences.updateData {
            it.copy { this.showSystemApps = shouldShowSystemApps }
        }
    }

    suspend fun setShowServiceInfo(shouldShowServiceInfo: Boolean) {
        userPreferences.updateData {
            it.copy { this.showServiceInfo = shouldShowServiceInfo }
        }
    }

    suspend fun setAppSorting(sorting: AppSorting) {
        userPreferences.updateData {
            it.copy {
                this.appSorting = when (sorting) {
                    AppSorting.NAME_ASCENDING -> AppSortingProto.APP_NAME_ASCENDING
                    AppSorting.NAME_DESCENDING -> AppSortingProto.APP_NAME_DESCENDING
                    AppSorting.FIRST_INSTALL_TIME_ASCENDING ->
                        AppSortingProto.FIRST_INSTALL_TIME_ASCENDING

                    AppSorting.FIRST_INSTALL_TIME_DESCENDING ->
                        AppSortingProto.FIRST_INSTALL_TIME_DESCENDING

                    AppSorting.LAST_UPDATE_TIME_ASCENDING ->
                        AppSortingProto.LAST_UPDATE_TIME_ASCENDING

                    AppSorting.LAST_UPDATE_TIME_DESCENDING ->
                        AppSortingProto.LAST_UPDATE_TIME_DESCENDING
                }
            }
        }
    }

    suspend fun setComponentShowPriority(priority: ComponentShowPriority) {
        userPreferences.updateData {
            it.copy {
                this.componentShowPriority = when (priority) {
                    ComponentShowPriority.ENABLED_COMPONENTS_FIRST ->
                        ComponentShowPriorityProto.ENABLED_COMPONENTS_FIRST

                    ComponentShowPriority.DISABLED_COMPONENTS_FIRST ->
                        ComponentShowPriorityProto.DISABLED_COMPONENTS_FIRST
                }
            }
        }
    }

    suspend fun setShowRunningAppsOnTop(showRunningAppsOnTop: Boolean) {
        userPreferences.updateData {
            it.copy { this.showRunningAppsOnTop = showRunningAppsOnTop }
        }
    }
}
