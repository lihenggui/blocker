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

package com.merxury.blocker.feature.impl.settings

import android.app.Application
import android.content.SharedPreferences
import com.merxury.blocker.core.analytics.NoOpAnalyticsHelper
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.model.data.UserEditableSettings
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestSyncManager
import com.merxury.blocker.feature.impl.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.impl.settings.SettingsUiState.Success
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val syncManager = TestSyncManager()
    private val blockerPreferences = mock<BlockerPreferencesDataSource>()
    private val analyticsHelper = NoOpAnalyticsHelper()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val appContext = mock<Application>()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        viewModel = SettingsViewModel(
            ioDispatcher = dispatcher,
            userDataRepository = userDataRepository,
            blockerPreferences = blockerPreferences,
            syncManager = syncManager,
            appContext = appContext,
            analyticsHelper = analyticsHelper,
        )
    }

    @Test
    fun settingsUiState_whenInitial_thenShowLoading() = runTest {
        assertEquals(
            Loading,
            viewModel.settingsUiState.value,
        )
    }

    @Test
    fun settingsUiState_whenSuccess_thenShowData() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }
        userDataRepository.sendUserData(defaultUserData)
        val defaultUserData = defaultUserData.toUserEditableSettings()
        val allowStatistics = viewModel.isAllowStatistics()
        assertEquals(
            Success(defaultUserData, allowStatistics),
            viewModel.settingsUiState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun updateRuleServerProvider_switchesProvider_resetsAndSyncs() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }
        // Initialize with GITHUB (the default)
        userDataRepository.sendUserData(defaultUserData)

        // Mock SharedPreferences for clearLastSyncTime
        val mockEditor = mock<SharedPreferences.Editor>()
        val mockPrefs = mock<SharedPreferences>()
        whenever(mockPrefs.edit()) doReturn mockEditor
        whenever(mockEditor.remove(any())) doReturn mockEditor
        whenever(appContext.getSharedPreferences(any<String>(), any())) doReturn mockPrefs

        // Switch to GITLAB
        viewModel.updateRuleServerProvider(RuleServerProvider.GITLAB)

        // Verify ruleCommitId was reset
        verify(blockerPreferences).resetRuleCommitId()
        // Verify immediate sync was requested
        assertTrue(syncManager.syncRequestedImmediately)
        // Verify the provider was updated
        val currentProvider = userDataRepository.userData
        launch(UnconfinedTestDispatcher()) { currentProvider.collect() }.cancel()

        collectJob.cancel()
    }
}

private fun UserPreferenceData.toUserEditableSettings() = UserEditableSettings(
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
