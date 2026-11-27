// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.test.repository.FakeOperationHistoryRepository
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestTimeZoneMonitor
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Instant

class HistoryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val history = FakeOperationHistoryRepository()
    private val rules = TestGeneralRuleRepository()
    private val timeZone = TestTimeZoneMonitor().apply { setTimeZone(TimeZone.of("America/Los_Angeles")) }
    private val viewModel = HistoryViewModel(
        history,
        FakeInstalledAppRepository(),
        FakeDeviceUserRepository(),
        FakeSelectedUserRepository(),
        rules,
        timeZone,
    )
    private val operation = Operation(
        id = 1,
        timestamp = Instant.parse("2026-03-08T07:59:00Z"),
        source = OperationSource.APP_DETAIL,
        ref = "com.example.app",
        entryCount = 1,
    )

    @Test
    fun givenOperationsAcrossMidnight_whenSubscribed_thenGroupsByLocalDayNewestFirst() = runTest {
        val later = operation.copy(id = 2, timestamp = Instant.parse("2026-03-08T08:01:00Z"))
        val sameTime = later.copy(id = 3)
        history.operations.value = listOf(later, operation, sameTime)

        viewModel.uiState.test {
            val days = checkNotNull(awaitState { it.days != null }.days)
            assertThat(days.map { it.date }).containsExactly(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-07")).inOrder()
            assertThat(days.map { day -> day.operations.map { it.operation.id } }).containsExactly(listOf(3L, 2L), listOf(1L)).inOrder()
        }
    }

    @Test
    fun givenProjectedCounts_whenSubscribed_thenShowsEachSummaryAndUndoneStatus() = runTest {
        history.operations.value = listOf(
            operation.copy(id = 4, blockedCount = 3, entryCount = 3),
            operation.copy(id = 3, enabledCount = 1),
            operation.copy(id = 2, blockedCount = 3, enabledCount = 1, entryCount = 4, undoneByOperationId = 8),
            operation.copy(entryCount = 2),
        )

        viewModel.uiState.test {
            val rows = checkNotNull(awaitState { it.days != null }.days).single().operations
            assertThat(rows.map { it.summary }).containsExactly(
                OperationSummary.Blocked(3),
                OperationSummary.Enabled(1),
                OperationSummary.Mixed(3, 1),
                OperationSummary.Changed(2),
            ).inOrder()
            assertThat(rows.map { it.isUndone }).containsExactly(false, false, true, false).inOrder()
        }
    }

    @Test
    fun givenOperationsLinkedToUndoRows_whenSubscribed_thenUsesOneHeadlineOrSeveralOperations() = runTest {
        val single = operation.copy(id = 4, source = OperationSource.UNDO, ref = null, entryCount = 0)
        val several = operation.copy(id = 5, source = OperationSource.UNDO, ref = null, entryCount = 3)
        history.operations.value = listOf(
            operation.copy(undoneByOperationId = single.id),
            operation.copy(id = 2, undoneByOperationId = several.id),
            operation.copy(id = 3, undoneByOperationId = several.id),
            single,
            several,
        )

        viewModel.uiState.test {
            val rows = checkNotNull(awaitState { it.days != null }.days).single().operations.associateBy { it.operation.id }
            assertThat(rows.getValue(single.id).summary)
                .isEqualTo(OperationSummary.Undo(UndoTarget.Single(OperationHeadline(OperationSource.APP_DETAIL, operation.ref)), 0))
            assertThat(rows.getValue(several.id).summary).isEqualTo(OperationSummary.Undo(UndoTarget.Several(2), 3))
        }
    }

    @Test
    fun givenPackageAndRuleRefs_whenSubscribed_thenShowsAppFallbackAndKnownRuleName() = runTest {
        val knownRule = operation.copy(id = 2, source = OperationSource.RULE_DETAIL, ref = "42")
        val missingRule = knownRule.copy(id = 3, ref = "99")
        history.operations.value = listOf(operation, knownRule, missingRule)
        rules.sendRuleList(listOf(GeneralRule(42, "Known rule")))

        viewModel.uiState.test {
            val state = awaitState { it.days?.single()?.operations?.any { row -> row.headline.reference == "Known rule" } == true }
            val refs = state.days?.single()?.operations?.associate { it.operation.id to it.headline.reference }
            assertThat(refs).containsExactly(1L, "com.example.app", 2L, "Known rule", 3L, null)
        }
    }
}

internal suspend fun <T> ReceiveTurbine<T>.awaitState(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
