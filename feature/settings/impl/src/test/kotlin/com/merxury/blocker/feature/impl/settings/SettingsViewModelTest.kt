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
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.NoOpAnalyticsHelper
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.UserEditableSettings
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.rule.backup.BlockerBackupSets
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestSyncManager
import com.merxury.blocker.feature.impl.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.impl.settings.SettingsUiState.Success
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val syncManager = TestSyncManager()
    private val analyticsHelper = NoOpAnalyticsHelper()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val appContext = mock<Application>()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        viewModel = SettingsViewModel(
            ioDispatcher = dispatcher,
            userDataRepository = userDataRepository,
            syncManager = syncManager,
            appContext = appContext,
            analyticsHelper = analyticsHelper,
            workManager = mock<WorkManager>(),
            savedStateHandle = SavedStateHandle(),
            selectedUsers = FakeSelectedUserRepository(),
            deviceUsers = FakeDeviceUserRepository(),
            backupSets = mock<BlockerBackupSets>(),
        )
    }

    @Test
    fun whenObservingInitialSettingsState_thenItShowsLoading() = runTest {
        assertEquals(
            Loading,
            viewModel.settingsUiState.value,
        )
    }

    @Test
    fun whenSettingsLoad_thenTheStateShowsTheSavedData() = runTest {
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
    fun givenSavedControllerType_whenSettingsShown_thenThePickerShowsIt() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.settingsUiState.collect() }

        for (type in ControllerType.entries) {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = type))

            assertEquals(type, (viewModel.settingsUiState.value as Success).settings.controllerType)
        }
        collectJob.cancel()
    }

    @Test
    fun givenGitHubSelected_whenSwitchingToGitLab_thenTheNewProviderIsCheckedImmediately() = runTest {
        userDataRepository.sendUserData(defaultUserData)

        viewModel.updateRuleServerProvider(RuleServerProvider.GITLAB)

        assertThat(userDataRepository.userData.first().ruleServerProvider).isEqualTo(RuleServerProvider.GITLAB)
        assertThat(syncManager.syncRequestedImmediately).isTrue()
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
