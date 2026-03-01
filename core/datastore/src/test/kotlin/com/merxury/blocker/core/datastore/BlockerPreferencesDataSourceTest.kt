/*
 * Copyright 2025 Blocker
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

import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockerPreferencesDataSourceTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var subject: BlockerPreferencesDataSource

    @Before
    fun setup() {
        subject =
            BlockerPreferencesDataSource(InMemoryDataStore(UserPreferences.getDefaultInstance()))
    }

    @Test
    fun ifwControllerShouldBeSelectedByDefault() = testScope.runTest {
        assertEquals(subject.userData.first().controllerType, ControllerType.IFW)
    }

    @Test
    fun topAppTypeShouldBeNoneByDefault() = testScope.runTest {
        assertEquals(subject.userData.first().topAppType, TopAppType.NONE)
    }

    @Test
    fun shouldShowServiceInfoIsFalseByDefault() = testScope.runTest {
        assertFalse(subject.userData.first().showServiceInfo)
    }

    @Test
    fun shouldEnableStatisticsIsTrueByDefault() = testScope.runTest {
        assertTrue(subject.userData.first().enableStatistics)
    }

    @Test
    fun shouldUseDynamicColorFalseByDefault() = testScope.runTest {
        assertFalse(subject.userData.first().useDynamicColor)
    }

    @Test
    fun userShouldUseDynamicColorIsTrueWhenSet() = testScope.runTest {
        subject.setDynamicColorPreference(true)
        assertTrue(subject.userData.first().useDynamicColor)
    }

    @Test
    fun darkThemeConfigShouldBeFollowSystemByDefault() = testScope.runTest {
        assertEquals(subject.userData.first().darkThemeConfig, DarkThemeConfig.FOLLOW_SYSTEM)
    }

    @Test
    fun userShouldSetDarkThemeConfigToLight() = testScope.runTest {
        subject.setDarkThemeConfig(DarkThemeConfig.LIGHT)
        assertEquals(subject.userData.first().darkThemeConfig, DarkThemeConfig.LIGHT)
    }

    @Test
    fun userShouldSetControllerTypeToPM() = testScope.runTest {
        subject.setControllerType(ControllerType.PM)
        assertEquals(subject.userData.first().controllerType, ControllerType.PM)
    }

    @Test
    fun userShouldSetRuleServerProviderToGitLab() = testScope.runTest {
        subject.setRuleServerProvider(RuleServerProvider.GITLAB)
        assertEquals(subject.userData.first().ruleServerProvider, RuleServerProvider.GITLAB)
    }

    @Test
    fun userShouldSetRuleBackupFolder() = testScope.runTest {
        val folder = "/backup/folder"
        subject.setRuleBackupFolder(folder)
        assertEquals(subject.userData.first().ruleBackupFolder, folder)
    }

    @Test
    fun userShouldSetBackupSystemAppToTrue() = testScope.runTest {
        subject.setBackupSystemApp(true)
        assertTrue(subject.userData.first().backupSystemApp)
    }

    @Test
    fun userShouldSetRestoreSystemAppToTrue() = testScope.runTest {
        subject.setRestoreSystemApp(true)
        assertTrue(subject.userData.first().restoreSystemApp)
    }

    @Test
    fun userShouldSetShowSystemAppsToTrue() = testScope.runTest {
        subject.setShowSystemApps(true)
        assertTrue(subject.userData.first().showSystemApps)
    }

    @Test
    fun userShouldSetAppSortingToFirstInstallTime() = testScope.runTest {
        subject.setAppSorting(AppSorting.FIRST_INSTALL_TIME)
        assertEquals(subject.userData.first().appSorting, AppSorting.FIRST_INSTALL_TIME)
    }

    @Test
    fun userShouldSetAppSortingOrderToDescending() = testScope.runTest {
        subject.setAppSortingOrder(SortingOrder.DESCENDING)
        assertEquals(subject.userData.first().appSortingOrder, SortingOrder.DESCENDING)
    }

    @Test
    fun userShouldSetComponentShowPriorityToEnabledComponentsFirst() = testScope.runTest {
        subject.setComponentShowPriority(ComponentShowPriority.ENABLED_COMPONENTS_FIRST)
        assertEquals(
            subject.userData.first().componentShowPriority,
            ComponentShowPriority.ENABLED_COMPONENTS_FIRST,
        )
    }

    @Test
    fun userShouldSetComponentSortingToPackageName() = testScope.runTest {
        subject.setComponentSorting(ComponentSorting.PACKAGE_NAME)
        assertEquals(subject.userData.first().componentSorting, ComponentSorting.PACKAGE_NAME)
    }

    @Test
    fun userShouldSetComponentSortingOrderToDescending() = testScope.runTest {
        subject.setComponentSortingOrder(SortingOrder.DESCENDING)
        assertEquals(subject.userData.first().componentSortingOrder, SortingOrder.DESCENDING)
    }

    @Test
    fun userShouldSetTopAppTypeToRunning() = testScope.runTest {
        subject.setTopAppType(TopAppType.RUNNING)
        assertEquals(subject.userData.first().topAppType, TopAppType.RUNNING)
    }

    @Test
    fun userShouldSetTopAppTypeToDisabled() = testScope.runTest {
        subject.setTopAppType(TopAppType.DISABLED)
        assertEquals(subject.userData.first().topAppType, TopAppType.DISABLED)
    }

    @Test
    fun userShouldSetIsFirstTimeInitializationCompletedToTrue() = testScope.runTest {
        subject.setIsFirstTimeInitializationCompleted(true)
        assertTrue(subject.userData.first().isFirstTimeInitializationCompleted)
    }

    @Test
    fun userShouldSetAppDisplayLanguage() = testScope.runTest {
        val language = "en"
        subject.setAppDisplayLanguage(language)
        assertEquals(subject.userData.first().appDisplayLanguage, language)
    }

    @Test
    fun userShouldSetLibDisplayLanguage() = testScope.runTest {
        val language = "fr"
        subject.setLibDisplayLanguage(language)
        assertEquals(subject.userData.first().libDisplayLanguage, language)
    }

    @Test
    fun userShouldSetEnableStatisticsToFalse() = testScope.runTest {
        subject.setEnableStatistics(false)
        assertFalse(subject.userData.first().enableStatistics)
    }
}
