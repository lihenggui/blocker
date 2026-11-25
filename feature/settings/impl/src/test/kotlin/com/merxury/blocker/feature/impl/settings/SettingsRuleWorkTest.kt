/*
 * Copyright 2026 Blocker
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
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.merxury.blocker.core.analytics.NoOpAnalyticsHelper
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.backup.BlockerBackupSets
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.rule.entity.resultMessage
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestSyncManager
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.settings.api.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** The rule work the settings screen starts, and the events it shows for it. */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRuleWorkTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userDataRepository = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    private val workManager = mock<WorkManager>()
    private val backupSets = mock<BlockerBackupSets>()
    private val selected = FakeSelectedUserRepository()
    private val deviceUsers = FakeDeviceUserRepository()
    private val owner = DeviceUser(UserId.SYSTEM, "Device Owner", UserKind.OWNER, null, running = true)
    private val work = DeviceUser(UserId(10), "Managed by Example", UserKind.WORK_PROFILE, UserId.SYSTEM, running = true)

    private val savedStateHandle = SavedStateHandle()
    private val viewModel by lazy { createViewModel(savedStateHandle) }

    private fun createViewModel(state: SavedStateHandle) = SettingsViewModel(
        appContext = application,
        userDataRepository = userDataRepository,
        syncManager = TestSyncManager(),
        analyticsHelper = NoOpAnalyticsHelper(),
        workManager = workManager,
        savedStateHandle = state,
        selectedUsers = selected,
        deviceUsers = deviceUsers,
        backupSets = backupSets,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )

    private fun ended(state: WorkInfo.State, vararg output: Pair<String, Any>) = MutableLiveData(listOf(WorkInfo(UUID.randomUUID(), state, emptySet(), workDataOf(*output))))

    private fun successfulEnqueue() = mock<Operation>().also {
        whenever(it.result) doReturn Futures.immediateFuture(Operation.SUCCESS)
    }

    private fun finishUserWork(vararg output: Pair<String, Any>) {
        val requests = mutableMapOf<String, UUID>()
        val operation = successfulEnqueue()
        whenever(workManager.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())).thenAnswer {
            requests[it.getArgument(0)] = it.getArgument<OneTimeWorkRequest>(2).id
            operation
        }
        whenever(workManager.getWorkInfoByIdFlow(any())).thenAnswer {
            flowOf(WorkInfo(it.getArgument(0), WorkInfo.State.SUCCEEDED, emptySet(), workDataOf(*output)))
        }
        whenever(workManager.getWorkInfosForUniqueWorkFlow(any())).thenAnswer {
            flowOf(listOf(WorkInfo(requests.getValue(it.getArgument(0)), WorkInfo.State.SUCCEEDED, emptySet(), workDataOf(*output))))
        }
    }

    private fun request() = argumentCaptor<OneTimeWorkRequest>().run {
        verify(workManager).enqueueUniqueWork(any<String>(), any(), capture())
        firstValue
    }

    private fun selectWork() {
        selected.sendUsers(listOf(owner, work))
        selected.select(work.id)
    }

    @Test
    fun givenMatImport_whenItFinishes_thenTheScreenGetsItsResult() = runTest {
        finishUserWork(RuleWorkResult.PARAM_CHANGED_COUNT to 3, RuleWorkResult.PARAM_SKIPPED_COUNT to 1)
        val events = mutableListOf<SettingsRuleEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.startRuleAction(SettingsRuleAction.IMPORT_MAT) {
            viewModel.importMyAndroidToolsRules(Uri.parse("content://docs/mat.txt"))
        }

        verify(workManager).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
        assertThat(events).containsExactly(SettingsRuleEvent(RuleWorkEvent(RuleWorkType.IMPORT_MAT_RULES, RuleWorkResult.FINISHED, changed = 3, skipped = 1)))
        assertThat(events.single().work.resultMessage(application.resources)).isEqualTo("Done. 3 items changed. 1 item skipped.")
    }

    @Test
    fun givenImportWithAnUnreadableFile_whenItFinishes_thenTheMessageCountsTheFailureInsteadOfAnError() = runTest {
        whenever(backupSets.alternatives(defaultUserData.ruleBackupFolder, UserId.SYSTEM)) doReturn emptyList()
        finishUserWork(
            RuleWorkResult.PARAM_CHANGED_COUNT to 0,
            RuleWorkResult.PARAM_UNCHANGED_COUNT to 145,
            RuleWorkResult.PARAM_FAILED_COUNT to 1,
        )
        val events = mutableListOf<SettingsRuleEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.startRuleAction(SettingsRuleAction.IMPORT)

        assertThat(events.single().work.resultMessage(application.resources)).isEqualTo("Done. 0 items changed. 1 item failed.")
    }

    @Test
    fun givenExportWithoutTheFirewall_whenItFinishes_thenTheMessageSaysTheRulesWereNotIncluded() = runTest {
        finishUserWork(RuleWorkResult.PARAM_CHANGED_COUNT to 7, RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE to true)
        val events = mutableListOf<SettingsRuleEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.startRuleAction(SettingsRuleAction.EXPORT)

        assertThat(events.single().work.resultMessage(application.resources))
            .isEqualTo("Done. 7 items changed. The Intent Firewall rules could not be read and were not included.")
    }

    @Test
    fun givenUnreachableFolder_whenImportFails_thenTheMessageSaysTheFolderCannotBeAccessed() = runTest {
        whenever(workManager.getWorkInfosForUniqueWorkLiveData("ImportIfwRule")) doReturn
            ended(WorkInfo.State.FAILED, RuleWorkResult.PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION)
        val events = mutableListOf<SettingsRuleEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.importIfwRules()

        assertThat(events.single().work.resultMessage(application.resources)).isEqualTo("Cannot access the backup folder. Please choose it again in settings.")
    }

    @Test
    fun givenWorkSelected_whenExportIsConfirmedAfterChannelLoss_thenWorkRemainsTheTarget() = runTest {
        selectWork()
        finishUserWork(RuleWorkResult.PARAM_CHANGED_COUNT to 2)

        viewModel.eventFlow.test {
            viewModel.startRuleAction(SettingsRuleAction.EXPORT)
            selected.sendUsers(listOf(owner))
            viewModel.confirmRuleAction { error("Export does not open a file picker") }

            assertThat(request().workSpec.input.getInt("param_target_user", -1)).isEqualTo(work.id.value)
            assertThat(awaitItem().target).isEqualTo(ProfileLabel.Work)
        }
    }

    @Test
    fun givenWorkSelected_whenResetIsConfirmed_thenOnlyTheWorkUserIsPassedToTheWorker() = runTest {
        selectWork()
        finishUserWork()

        viewModel.startRuleAction(SettingsRuleAction.RESET_PM)
        viewModel.confirmRuleAction { error("Reset does not open a file picker") }

        assertThat(request().workSpec.input.getInt("param_target_user", -1)).isEqualTo(work.id.value)
    }

    @Test
    fun givenWorkSelected_whenMatPickerReturnsAfterSelectionChanges_thenWorkRemainsTheTarget() = runTest {
        selectWork()
        finishUserWork()
        var opened = false

        viewModel.startRuleAction(SettingsRuleAction.IMPORT_MAT) { error("The user must confirm first") }
        viewModel.confirmRuleAction { opened = true }
        selected.select(owner.id)
        viewModel.importMyAndroidToolsRules(Uri.parse("content://docs/mat.txt"))

        assertThat(opened).isTrue()
        assertThat(request().workSpec.input.getInt("param_target_user", -1)).isEqualTo(work.id.value)
    }

    @Test
    fun givenWorkMatPickerOpen_whenViewModelIsRecreated_thenTheFileStillImportsForWork() = runTest {
        selectWork()
        deviceUsers.sendUsers(listOf(owner, work))
        finishUserWork()
        viewModel.startRuleAction(SettingsRuleAction.IMPORT_MAT)
        viewModel.confirmRuleAction {}
        val recreated = createViewModel(SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle.get<Any>(it) }))
        selected.sendUsers(listOf(owner))
        val file = Uri.parse("content://docs/mat.txt")

        recreated.eventFlow.test {
            recreated.importMyAndroidToolsRules(file)

            assertThat(awaitItem().target).isEqualTo(ProfileLabel.Work)
            assertThat(request().workSpec.input.getInt("param_target_user", -1)).isEqualTo(work.id.value)
            recreated.importMyAndroidToolsRules(file)
            expectNoEvents()
        }
    }

    @Test
    fun givenWorkResetAlreadyRunning_whenTappedAgain_thenTheKeptWorkShowsProgressAndOneResult() = runTest {
        selectWork()
        val workId = UUID.randomUUID()
        val workName = "ResetPm-user-${work.id.value}"
        val keptWork = MutableStateFlow(listOf(WorkInfo(workId, WorkInfo.State.RUNNING, emptySet())))
        val operation = successfulEnqueue()
        whenever(workManager.enqueueUniqueWork(eq(workName), eq(ExistingWorkPolicy.KEEP), any<OneTimeWorkRequest>())) doReturn operation
        whenever(workManager.getWorkInfosForUniqueWorkFlow(workName)) doReturn keptWork

        viewModel.eventFlow.test {
            repeat(2) {
                viewModel.startRuleAction(SettingsRuleAction.RESET_PM)
                viewModel.confirmRuleAction {}
                assertThat(awaitItem()).isEqualTo(SettingsRuleEvent(RuleWorkEvent(RuleWorkType.RESET_PM, RuleWorkResult.STARTED), ProfileLabel.Work))
            }
            verify(workManager, times(2)).enqueueUniqueWork(eq(workName), eq(ExistingWorkPolicy.KEEP), any<OneTimeWorkRequest>())
            keptWork.value = listOf(WorkInfo(workId, WorkInfo.State.SUCCEEDED, emptySet(), workDataOf(RuleWorkResult.PARAM_CHANGED_COUNT to 2)))

            assertThat(awaitItem()).isEqualTo(SettingsRuleEvent(RuleWorkEvent(RuleWorkType.RESET_PM, RuleWorkResult.FINISHED, changed = 2), ProfileLabel.Work))
            expectNoEvents()
        }
    }

    @Test
    fun givenOtherBackupSets_whenASetIsChosen_thenItIsImportedIntoTheOriginalSelectedUser() = runTest {
        selectWork()
        val known = DeviceUser(UserId(11), "Archive", UserKind.SECONDARY, null, running = false)
        deviceUsers.sendUsers(listOf(owner, work, known))
        val absent = BackupSet(UserId(12))
        whenever(backupSets.alternatives(defaultUserData.ruleBackupFolder, work.id)) doReturn listOf(BackupSet.OWNER, BackupSet(known.id), absent)
        finishUserWork()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.settingsUiState.collect { } }
        runCurrent()
        assertThat((viewModel.settingsUiState.value as SettingsUiState.Success).ruleDialog).isNull()

        viewModel.startRuleAction(SettingsRuleAction.IMPORT)
        selected.select(owner.id)
        runCurrent()

        val dialog = (viewModel.settingsUiState.value as SettingsUiState.Success).ruleDialog as SettingsRuleDialog.ChooseBackup
        assertThat(dialog.target).isEqualTo(ProfileLabel.Work)
        assertThat(dialog.sets).containsExactly(
            BackupSet.OWNER,
            ProfileLabel.Personal,
            BackupSet(known.id),
            ProfileLabel.Named("Archive"),
            absent,
            ProfileLabel.Numbered(12),
        )
        viewModel.chooseBackup(absent)
        val input = request().workSpec.input
        assertThat(input.getInt("param_source_set_user", -1)).isEqualTo(12)
        assertThat(input.getInt("param_target_user", -1)).isEqualTo(work.id.value)
    }

    @Test
    fun givenNoAlternativeSet_whenWorkImportIsConfirmed_thenTheWorkerUsesTheTargetsOwnSet() = runTest {
        selectWork()
        whenever(backupSets.alternatives(defaultUserData.ruleBackupFolder, work.id)) doReturn emptyList()
        finishUserWork()

        viewModel.startRuleAction(SettingsRuleAction.IMPORT)
        viewModel.confirmRuleAction { error("Blocker import does not open a file picker") }

        val input = request().workSpec.input
        assertThat(input.getInt("param_source_set_user", -1)).isEqualTo(-1)
        assertThat(input.getInt("param_target_user", -1)).isEqualTo(work.id.value)
    }

    @Test
    fun givenResetPmSetting_whenSummaryIsRead_thenItDescribesTheComponentsBlockerDisabled() {
        assertThat(application.getString(R.string.feature_settings_api_reset_pm_summary))
            .isEqualTo("Restore the components that Blocker disabled via PM to their default state")
    }
}
