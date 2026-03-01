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

package com.merxury.blocker.core.data.respository.userdata

import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.logAppDisplayLanguageChanged
import com.merxury.blocker.core.data.respository.logAppSortingChanged
import com.merxury.blocker.core.data.respository.logAppSortingOrderChanged
import com.merxury.blocker.core.data.respository.logBackupSystemAppPreferenceChanged
import com.merxury.blocker.core.data.respository.logComponentShowPriorityPreferenceChanged
import com.merxury.blocker.core.data.respository.logComponentSortingOrderPreferenceChanged
import com.merxury.blocker.core.data.respository.logComponentSortingPreferenceChanged
import com.merxury.blocker.core.data.respository.logControllerTypeChanged
import com.merxury.blocker.core.data.respository.logDarkThemeConfigChanged
import com.merxury.blocker.core.data.respository.logDynamicColorPreferenceChanged
import com.merxury.blocker.core.data.respository.logFirstTimeInitializationCompleted
import com.merxury.blocker.core.data.respository.logLibDisplayLanguageChanged
import com.merxury.blocker.core.data.respository.logRestoreSystemAppPreferenceChanged
import com.merxury.blocker.core.data.respository.logRuleServerProviderChanged
import com.merxury.blocker.core.data.respository.logShowServiceInfoPreferenceChanged
import com.merxury.blocker.core.data.respository.logShowSystemAppPreferenceChanged
import com.merxury.blocker.core.data.respository.logTopAppTypeChanged
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

val LIB_SUPPORTED_LANGUAGE = listOf(
    Locale.ENGLISH.toLanguageTag(),
    Locale.SIMPLIFIED_CHINESE.toLanguageTag(),
)

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

    override suspend fun setRuleBackupFolder(folder: String) = blockerPreferenceDataSource.setRuleBackupFolder(folder)

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

    override suspend fun setAppSortingOrder(order: SortingOrder) {
        blockerPreferenceDataSource.setAppSortingOrder(order)
        analyticsHelper.logAppSortingOrderChanged(order.name)
    }

    override suspend fun setComponentShowPriority(priority: ComponentShowPriority) {
        blockerPreferenceDataSource.setComponentShowPriority(priority)
        analyticsHelper.logComponentShowPriorityPreferenceChanged(priority.name)
    }

    override suspend fun setComponentSorting(sorting: ComponentSorting) {
        blockerPreferenceDataSource.setComponentSorting(sorting)
        analyticsHelper.logComponentSortingPreferenceChanged(sorting.name)
    }

    override suspend fun setComponentSortingOrder(order: SortingOrder) {
        blockerPreferenceDataSource.setComponentSortingOrder(order)
        analyticsHelper.logComponentSortingOrderPreferenceChanged(order.name)
    }

    override suspend fun setTopAppType(topAppType: TopAppType) {
        blockerPreferenceDataSource.setTopAppType(topAppType)
        analyticsHelper.logTopAppTypeChanged(topAppType.name)
    }

    override suspend fun setIsFirstTimeInitializationCompleted(completed: Boolean) {
        blockerPreferenceDataSource.setIsFirstTimeInitializationCompleted(completed)
        if (completed) {
            analyticsHelper.logFirstTimeInitializationCompleted()
        }
    }

    override suspend fun setAppDisplayLanguage(language: String) {
        blockerPreferenceDataSource.setAppDisplayLanguage(language)
        analyticsHelper.logAppDisplayLanguageChanged(language)
    }

    override suspend fun setLibDisplayLanguage(language: String) {
        blockerPreferenceDataSource.setLibDisplayLanguage(language)
        analyticsHelper.logLibDisplayLanguageChanged(language)
    }

    override suspend fun getLibDisplayLanguage(): String {
        val displayLanguageInSettings = userData.first().libDisplayLanguage
        if (displayLanguageInSettings.isNotBlank()) {
            return displayLanguageInSettings
        }
        // Empty means follow the system language first
        // If no matching found, fallback to English
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        val systemLanguage = if (country.isNotBlank()) {
            "$language-$country"
        } else {
            language
        }
        var splittedLanguage = systemLanguage.split("-")
        while (splittedLanguage.isNotEmpty()) {
            val languageTag = splittedLanguage.joinToString("-")
            val matchingLanguage = LIB_SUPPORTED_LANGUAGE.find { it == languageTag }
            if (matchingLanguage != null) {
                return matchingLanguage
            } else {
                splittedLanguage = splittedLanguage.dropLast(1)
            }
        }
        return Locale.ENGLISH.toLanguageTag()
    }

    override suspend fun setEnableStatistics(allow: Boolean) {
        blockerPreferenceDataSource.setEnableStatistics(allow)
    }
}
