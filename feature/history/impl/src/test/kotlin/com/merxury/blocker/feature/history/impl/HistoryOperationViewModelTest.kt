// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.history.OperationHistoryRepository
import com.merxury.blocker.core.data.test.repository.FakeOperationHistoryRepository
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestTimeZoneMonitor
import com.merxury.blocker.core.ui.profile.ProfileLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class HistoryOperationViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val operation = Operation(1, Instant.parse("2026-03-08T08:00:00Z"), OperationSource.SEARCH, entryCount = 1)
    private val later = operation.copy(id = 2, timestamp = Instant.parse("2026-03-09T08:00:00Z"))
    private val history = FakeOperationHistoryRepository().apply { operations.value = listOf(operation) }
    private val components = FakeComponentStatusRepository()
    private val apps = FakeInstalledAppRepository()
    private val users = FakeDeviceUserRepository()

    private fun viewModel(repository: OperationHistoryRepository = history) = HistoryOperationViewModel(
        operation.id,
        repository,
        components,
        apps,
        users,
        FakeSelectedUserRepository(),
        TestGeneralRuleRepository(),
        TestTimeZoneMonitor(),
    )

    @Test
    fun givenUndoableOperation_whenUndoTapped_thenRunsImmediatelyAndShowsResultMessage() = runTest {
        val result = UndoResult(restored = 2, alreadyAtTarget = 1, skipped = 3, failed = 4)
        val gate = CompletableDeferred<Unit>()
        history.undoResult = result
        val repository = object : OperationHistoryRepository by history {
            override suspend fun undoOperation(id: Long): UndoResult {
                val response = history.undoOperation(id)
                gate.await()
                return response
            }
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitState { it.canUndo }
            viewModel.undo()
            viewModel.undo()
            assertThat(awaitState { it.isUndoing }.confirmLater).isFalse()
            assertThat(history.undoIds).containsExactly(operation.id)
            gate.complete(Unit)

            val finished = awaitState { !it.isUndoing && it.message != null }
            assertThat((finished.message as HistoryMessage.Undone).result).isEqualTo(result)
            viewModel.messageShown(finished.message)
            awaitState { it.message == null }
        }
    }

    @Test
    fun givenIneligibleNeighbors_whenNewerUndoableOperationAppears_thenLaterActionIsAvailable() = runTest {
        history.operations.value += listOf(
            operation.copy(id = 3, timestamp = Instant.parse("2026-03-07T08:00:00Z")),
            operation.copy(id = 4),
            later.copy(id = 5, source = OperationSource.UNDO),
            later.copy(id = 6, undoneByOperationId = 5),
            later.copy(id = 7, entryCount = 0),
        )
        viewModel().uiState.test {
            assertThat(awaitState { it.canUndo }.canUndoLater).isFalse()

            history.operations.value += later

            assertThat(awaitState { it.canUndoLater }.laterCount).isEqualTo(2)
        }
    }

    @Test
    fun givenLaterUndoRequested_whenConfirmed_thenUndoesSinceTheOperationTimestamp() = runTest {
        history.operations.value += later
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitState { it.canUndoLater }
            viewModel.requestUndoLater()
            assertThat(awaitState { it.confirmLater }.laterCount).isEqualTo(1)

            viewModel.confirmUndoLater()

            assertThat(awaitState { it.message is HistoryMessage.Undone }.confirmLater).isFalse()
            assertThat(history.undoStarts).containsExactly(operation.timestamp)
            assertThat(history.undoIds).isEmpty()
        }
    }

    @Test
    fun givenLaterUndoRequested_whenCancelled_thenDoesNotCallRepository() = runTest {
        history.operations.value += later
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitState { it.canUndoLater }
            viewModel.requestUndoLater()
            awaitState { it.confirmLater }
            viewModel.cancelUndoLater()
            viewModel.confirmUndoLater()

            assertThat(awaitState { !it.confirmLater }.message).isNull()
            assertThat(history.undoIds).isEmpty()
            assertThat(history.undoStarts).isEmpty()
        }
    }

    @Test
    fun givenUndoableOperation_whenUndoIsRecorded_thenTimeReplacesActions() = runTest {
        history.operations.value += later
        val undo = later.copy(id = 9, source = OperationSource.UNDO)
        viewModel().uiState.test {
            awaitState { it.canUndoLater }

            history.operations.value = listOf(undo, later, operation.copy(undoneByOperationId = undo.id))

            val undone = awaitState { it.row?.isUndone == true }
            assertThat(undone.canUndo).isFalse()
            assertThat(undone.canUndoLater).isFalse()
            assertThat(undone.undoneAt).isEqualTo(undo.timestamp)
        }
    }

    @Test
    fun givenUndoThrows_whenUndoTapped_thenShowsFailureAndLeavesActionAvailable() = runTest {
        val repository = object : OperationHistoryRepository by history {
            override suspend fun undoOperation(id: Long): UndoResult = error("Undo failed")
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitState { it.canUndo }
            viewModel.undo()

            val failed = awaitState { !it.isUndoing && it.message is HistoryMessage.UndoFailed }
            assertThat(failed.canUndo).isTrue()
        }
    }

    @Test
    fun givenEntriesAcrossAppsAndProfiles_whenLoaded_thenGroupsByAppAndUsesManifestAndProfileLabels() = runTest {
        val work = UserId(10)
        val sync = ComponentId("com.example.app", "com.example.app.SyncService")
        val removed = sync.copy(className = "com.example.app.RemovedService")
        val sharing = ComponentId("com.example.other", "com.example.other.ShareService")
        val default = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
        val enabled = default.copy(pm = PmSetting.ENABLED)
        val disabled = default.copy(pm = PmSetting.DISABLED)
        val entries = listOf(
            OperationEntry(operation.id, ComponentKey(sync, work), SERVICE, default, enabled),
            OperationEntry(operation.id, ComponentKey(removed, work), SERVICE, enabled, ControlState.UNKNOWN),
            OperationEntry(operation.id, ComponentKey(sharing, UserId.SYSTEM), SERVICE, enabled, disabled),
        )
        history.entries[operation.id] = entries
        users.sendUsers(listOf(DeviceUser(work, "Managed profile", UserKind.WORK_PROFILE, UserId.SYSTEM, running = true)))
        components.sendComponents(UserId.SYSTEM, listOf(ComponentStatus(ComponentDeclaration(sync, SERVICE, false, false), enabled)))
        apps.sendApps(listOf(installedApp(sync.packageName, work), installedApp(sharing.packageName, UserId.SYSTEM)))

        viewModel().uiState.test {
            val groups = checkNotNull(awaitState { it.groups != null }.groups)
            assertThat(groups.map { it.appIcon.packageName }).containsExactly(sync.packageName, sharing.packageName).inOrder()
            assertThat(groups.first().entries).containsExactly(
                HistoryEntry(entries[0].key, ProfileLabel.Work, ComponentState.ON),
                HistoryEntry(entries[1].key, ProfileLabel.Work, ComponentState.UNKNOWN),
            ).inOrder()
            assertThat(groups.last().entries.single().change).isEqualTo(ComponentState.OFF)
        }
    }

    @Test
    fun givenOneDeviceUser_whenEntriesLoad_thenOmitsProfileLabels() = runTest {
        val component = ComponentId("com.example.app", "com.example.app.SyncService")
        val on = ControlState(PmSetting.ENABLED, IfwBlock.NOT_BLOCKED)
        val off = on.copy(pm = PmSetting.DISABLED)
        history.entries[operation.id] = listOf(
            OperationEntry(operation.id, ComponentKey(component, UserId.SYSTEM), SERVICE, on, off),
        )

        viewModel().uiState.test {
            val groups = checkNotNull(awaitState { it.groups != null }.groups)
            assertThat(groups.single().entries.single().profile).isNull()
        }
    }

    private fun installedApp(packageName: String, user: UserId) = InstalledApp(
        key = AppKey(packageName, user),
        uid = null,
        label = packageName,
        versionName = "1",
        versionCode = 1,
        isSystem = false,
        enabled = true,
        enabledSetting = AppEnabledSetting.DEFAULT,
        apkPath = null,
        dataDir = null,
        firstInstallTime = operation.timestamp,
        lastUpdateTime = operation.timestamp,
        minSdk = 24,
        targetSdk = 36,
    )
}
