// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import app.cash.turbine.ReceiveTurbine
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
import com.merxury.blocker.core.model.data.GeneralRule
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
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Instant

class HistoryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val history = FakeOperationHistoryRepository()
    private val repository = TrackingHistory(history)
    private val components = FakeComponentStatusRepository()
    private val apps = FakeInstalledAppRepository()
    private val users = FakeDeviceUserRepository()
    private val rules = TestGeneralRuleRepository()
    private val timeZone = TestTimeZoneMonitor().apply { setTimeZone(TimeZone.of("America/Los_Angeles")) }
    private val viewModel = HistoryViewModel(repository, components, apps, users, FakeSelectedUserRepository(), rules, timeZone)
    private val operation = Operation(
        id = 1,
        timestamp = Instant.parse("2026-03-08T07:59:00Z"),
        source = OperationSource.APP_DETAIL,
        ref = "com.example.app",
        entryCount = 1,
    )
    private val workUser = UserId(10)
    private val component = ComponentId("com.example.app", "com.example.app.SyncService")

    @Test
    fun givenOperations_whenSubscribed_thenUiStateGroupsByDay() = runTest {
        val later = operation.copy(id = 2, timestamp = Instant.parse("2026-03-08T08:01:00Z"))
        val sameDay = later.copy(id = 3)
        history.operations.value = listOf(later, operation, sameDay)

        viewModel.uiState.test {
            val days = checkNotNull(awaitState { it.days != null }.days)
            assertThat(days.map { it.date }).containsExactly(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-07")).inOrder()
            assertThat(days.map { day -> day.operations.map { it.operation.id } }).containsExactly(listOf(3L, 2L), listOf(1L)).inOrder()
        }
    }

    @Test
    fun givenUndoConfirmed_whenFinished_thenResultMessageShown() = runTest {
        history.operations.value = listOf(operation)
        val result = UndoResult(restored = 2, alreadyAtTarget = 1, skipped = 3, failed = 4)
        history.undoResult = result
        repository.undoGate = CompletableDeferred()

        viewModel.uiState.test {
            val row = awaitRow()
            viewModel.requestUndo(row)
            assertThat(awaitState { it.confirmation != null }.confirmation).isEqualTo(UndoRequest.Single(operation.id, 1))

            viewModel.confirmUndo()
            viewModel.confirmUndo()
            val busy = awaitState { it.isUndoing }
            assertThat(busy.confirmation).isNull()

            repository.undoGate?.complete(Unit)
            val finished = awaitState { !it.isUndoing && it.message is HistoryMessage.Undone }
            assertThat((finished.message as HistoryMessage.Undone).result).isEqualTo(result)
            assertThat(repository.undoIds).containsExactly(operation.id)
            viewModel.messageShown(finished.message)
            awaitState { it.message == null }
        }
    }

    @Test
    fun givenDayUndo_whenConfirmed_thenUndoSinceUsesStartOfThatDay() = runTest {
        val day = LocalDate.parse("2026-03-08")
        history.operations.value = listOf(operation.copy(timestamp = Instant.parse("2026-03-08T20:00:00Z")))
        viewModel.uiState.test {
            awaitRow()
            viewModel.requestUndoSince(day)
            val start = Instant.parse("2026-03-08T08:00:00Z")
            assertThat(awaitState { it.confirmation != null }.confirmation).isEqualTo(UndoRequest.Since(day, start))

            viewModel.confirmUndo()
            awaitState { it.message is HistoryMessage.Undone }
            assertThat(repository.undoStarts).containsExactly(start)
            assertThat(repository.undoIds).isEmpty()
        }
    }

    @Test
    fun givenUndoOperationRow_thenNoUndoAction() = runTest {
        history.operations.value = listOf(operation.copy(source = OperationSource.UNDO, ref = "12"))
        viewModel.uiState.test {
            val row = awaitRow()
            assertThat(row.canUndo).isFalse()
            assertThat(uiDays().single().canUndoSince).isFalse()
            viewModel.requestUndo(row)
            viewModel.requestUndoSince(uiDays().single().date)

            testScheduler.runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun givenRecordedEntries_whenExpanded_thenLoadsChangesUsingCachedManifestAndProfile() = runTest {
        val key = ComponentKey(component, workUser)
        val default = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
        val enabled = default.copy(pm = PmSetting.ENABLED)
        val entry = OperationEntry(operation.id, key, SERVICE, default, enabled)
        val uncached = component.copy(className = "com.example.app.RemovedService")
        history.operations.value = listOf(operation.copy(entryCount = 2))
        history.entries[operation.id] = listOf(entry, entry.copy(key = key.copy(id = uncached), after = ControlState.UNKNOWN))
        users.sendUsers(listOf(DeviceUser(workUser, "Managed profile", UserKind.WORK_PROFILE, UserId.SYSTEM, running = true)))
        components.sendComponents(UserId.SYSTEM, listOf(ComponentStatus(ComponentDeclaration(component, SERVICE, false, false), enabled)))
        apps.sendApps(listOf(installedApp()))

        viewModel.uiState.test {
            val row = awaitRow()
            assertThat(repository.entryIds).isEmpty()
            assertThat(row.reference).isEqualTo("Example App")
            viewModel.toggleOperation(row)

            val expansion = awaitState { it.expanded?.entries != null }.expanded
            assertThat(repository.entryIds).containsExactly(operation.id)
            assertThat(expansion?.entries).containsExactly(
                HistoryEntry("SyncService", "Example App", ProfileLabel.Work, ComponentState.OFF, ComponentState.ON),
                HistoryEntry("RemovedService", "Example App", ProfileLabel.Work, ComponentState.ON, ComponentState.UNKNOWN),
            ).inOrder()
            viewModel.toggleOperation(row)
            assertThat(awaitState { it.expanded == null }.expanded).isNull()
        }
    }

    @Test
    fun givenUndoRequested_whenCancelled_thenDoesNotCallRepository() = runTest {
        history.operations.value = listOf(operation)
        viewModel.uiState.test {
            viewModel.requestUndo(awaitRow())
            awaitState { it.confirmation != null }

            viewModel.cancelUndo()
            val cancelled = awaitState { it.confirmation == null }
            viewModel.confirmUndo()

            assertThat(cancelled.isUndoing).isFalse()
            assertThat(cancelled.message).isNull()
            assertThat(repository.undoIds).isEmpty()
            assertThat(repository.undoStarts).isEmpty()
        }
    }

    @Test
    fun givenPackageAndRuleRefs_whenSubscribed_thenShowsAppFallbackAndKnownRuleName() = runTest {
        val knownRule = operation.copy(id = 2, source = OperationSource.RULE_DETAIL, ref = "42")
        val missingRule = knownRule.copy(id = 3, ref = "99")
        history.operations.value = listOf(operation, knownRule, missingRule)
        rules.sendRuleList(listOf(GeneralRule(42, "Known rule")))

        viewModel.uiState.test {
            val day = awaitState { it.days?.single()?.operations?.any { row -> row.reference == "Known rule" } == true }
            val refs = day.days?.single()?.operations?.associate { it.operation.id to it.reference }
            assertThat(refs).containsExactly(1L, "com.example.app", 2L, "Known rule", 3L, null)
        }
    }

    private fun uiDays() = checkNotNull(viewModel.uiState.value.days)

    private suspend fun ReceiveTurbine<HistoryUiState>.awaitRow() = checkNotNull(awaitState { it.days != null }.days).first().operations.first()

    private suspend fun ReceiveTurbine<HistoryUiState>.awaitState(predicate: (HistoryUiState) -> Boolean): HistoryUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun installedApp() = InstalledApp(
        key = AppKey(component.packageName, workUser),
        uid = null,
        label = "Example App",
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

    private class TrackingHistory(
        private val fake: FakeOperationHistoryRepository,
    ) : OperationHistoryRepository by fake {
        val undoIds = mutableListOf<Long>()
        val undoStarts = mutableListOf<Instant>()
        val entryIds = mutableListOf<Long>()
        var undoGate: CompletableDeferred<Unit>? = null

        override suspend fun getEntries(operationId: Long): List<OperationEntry> {
            entryIds += operationId
            return fake.getEntries(operationId)
        }

        override suspend fun undoOperation(id: Long): UndoResult {
            undoIds += id
            undoGate?.await()
            return fake.undoOperation(id)
        }

        override suspend fun undoSince(since: Instant): UndoResult {
            undoStarts += since
            return fake.undoSince(since)
        }
    }
}
