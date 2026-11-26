// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.domain.wake.ObserveAppWakeActivityUseCase
import com.merxury.blocker.core.domain.wake.ObserveAppWakeStateUseCase
import com.merxury.blocker.core.domain.wake.ObserveWakeAvailabilityUseCase
import com.merxury.blocker.core.domain.wake.RestoreAppWakeChangesUseCase
import com.merxury.blocker.core.domain.wake.SetWakePathsBlockedUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.testing.repository.device.FakeAppRestrictionRepository
import com.merxury.blocker.core.testing.repository.device.FakeBackgroundUsageRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.repository.wake.FakeAutomaticWakeBlockRepository
import com.merxury.blocker.core.testing.repository.wake.FakeWakeBlockRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.wake.api.R.string
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class WakeAppViewModelTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    private val manual = FakeWakeBlockRepository()
    private val automatic = FakeAutomaticWakeBlockRepository()
    private var finishBlock: CompletableDeferred<Unit>? = null
    private val manualBatch = object : WakeBlockRepository by manual {
        override suspend fun blockRecordingChanges(requests: List<Pair<WakePath, WakeBlockMethod>>) = manual.blockRecordingChanges(requests).also { finishBlock?.await() }
    }
    private val background = FakeBackgroundUsageRepository()
    private val appKey = AppKey(DETAIL_PACKAGE, UserId.SYSTEM)
    private val first = detailPath("restart")
    private val second = detailPath("self", component = "$DETAIL_PACKAGE.Sync")
    private val known = AutomaticWakeBlock("known", "peer", DETAIL_PACKAGE, "$DETAIL_PACKAGE.Known", "bind", false, true)
    private val timed = detailMethod(OwnWakeKind.TIMED, detailPath("alarm", method = WakeBlockMethod.BACKGROUND_RESTRICTION, component = null))
    private var methods = listOf(detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, first), detailMethod(OwnWakeKind.SELF_START, second))
    private val base by lazy { detailState(own = methods) }
    private val observed = combine(manual.blocks, automatic.blocks, background.observe(appKey)) { blocks, reviewed, usage ->
        val paths = base.paths.mapValues { (key, state) ->
            val block = blocks.firstOrNull { it.pathKey == key }
            when {
                block != null -> state.copy(block = WakePathBlock.USER, blockedAtMillis = block.blockedAtMillis)
                state.path.blockMethod == WakeBlockMethod.BACKGROUND_RESTRICTION && usage?.allowed == false -> state.copy(block = WakePathBlock.USER)
                else -> state
            }
        }
        base.copy(paths = paths, automatic = reviewed, allBlocked = paths.values.all { it.block != WakePathBlock.NONE } && reviewed.all { it.active })
    }
    private val viewModel by lazy {
        val state = mock<ObserveAppWakeStateUseCase> { on { invoke(DETAIL_PACKAGE, 7) } doReturn observed }
        val activity = mock<ObserveAppWakeActivityUseCase> { on { invoke(DETAIL_PACKAGE, 7) } doReturn flowOf(base.activity) }
        val availability = mock<ObserveWakeAvailabilityUseCase> { on { invoke() } doReturn flowOf(WakeAvailability.Ready(200)) }
        val details = mock<ComponentDetailRepository> { on { getLocalComponentDetails(any()) } doReturn flowOf(emptyMap()) }
        WakeAppViewModel(
            DETAIL_PACKAGE, state, availability, FakeInstalledAppRepository(), FakeSelectedUserRepository(), details,
            background, SetWakePathsBlockedUseCase(manualBatch, automatic, background),
            RestoreAppWakeChangesUseCase(activity, manual, automatic, background, FakeAppRestrictionRepository()),
        )
    }

    @After
    fun clearViewModel() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun givenAllowedFirewallPath_whenTogglingBothWays_thenUpdatesRowWithoutReadingBackgroundUsage() = runTest {
        observe()
        val key = current.methods.first().key
        viewModel.toggle(key, allowed = false)
        runCurrent()
        assertThat(current.methods.first().blocked).isTrue()
        assertThat(current.notice).isNull()
        viewModel.toggle(key, allowed = true)
        runCurrent()
        assertThat(current.methods.first().blocked).isFalse()
        assertThat(current.notice).isNull()
        assertThat(background.reads).isEmpty()
    }

    @Test
    fun givenThreeChoices_whenBlockingThenUndoing_thenShowsRealRowsAndRestoresOnlyThatBatch() = runTest {
        val existing = detailPath("outside").record()
        manual.blocks.value = listOf(existing)
        automatic.blocks.value = listOf(known)
        observe()
        viewModel.blockAll(current.choices.mapTo(mutableSetOf()) { it.key })
        runCurrent()
        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly("outside", "restart", "self")
        assertThat(automatic.allowedIds).isEmpty()
        assertThat(current.rows.map { it.blocked }).containsExactly(true, true, true).inOrder()
        assertThat(current.background?.allowed).isFalse()
        val notice = requireNotNull(current.notice)
        assertThat(notice.text).isEqualTo(blockedCount(3))

        viewModel.undo(notice.id)
        runCurrent()
        assertThat(manual.blocks.value).containsExactly(existing)
        assertThat(automatic.allowedIds).containsExactly("known")
        assertThat(current.background?.allowed).isTrue()
        assertThat(background.writes).containsExactly(appKey to false, appKey to true).inOrder()
        assertThat(background.reads).isEmpty()
    }

    @Test
    fun givenTimedRowInBlockSheet_whenBlockingAndUndoing_thenWritesTheSettingOnceEachWay() = runTest {
        methods = methods + timed
        observe()
        val row = current.methods.single { it.kind == OwnWakeKind.TIMED }
        assertThat(current.choices.map { it.key }).contains(row.key)
        assertThat(current.choices.map { it.key }).doesNotContain(BACKGROUND_CHOICE)

        viewModel.blockAll(setOf(row.key))
        runCurrent()
        assertThat(current.methods.single { it.key == row.key }.blocked).isTrue()
        assertThat(current.background?.allowed).isFalse()
        assertThat(background.writes).containsExactly(appKey to false)
        val notice = requireNotNull(current.notice)
        assertThat(notice.text).isEqualTo(blockedCount(1))

        viewModel.undo(notice.id)
        runCurrent()
        assertThat(current.methods.single { it.key == row.key }.blocked).isFalse()
        assertThat(current.background?.allowed).isTrue()
        assertThat(background.writes).containsExactly(appKey to false, appKey to true).inOrder()
        assertThat(background.reads).containsExactly(appKey) // The batch captures the value for undo.
        assertThat(manual.blocks.value).isEmpty()
    }

    @Test
    fun givenBackgroundRestrictionRow_whenToggling_thenCardFollowsWithoutAnExtraRead() = runTest {
        methods = methods + timed
        observe()
        val row = current.methods.last()
        assertThat(current.background?.allowed).isTrue()

        viewModel.toggle(row.key, allowed = false)
        runCurrent()

        assertThat(current.methods.last().blocked).isTrue()
        assertThat(current.background?.allowed).isFalse()
        assertThat(background.writes).containsExactly(appKey to false)
        assertThat(background.reads).isEmpty()
    }

    @Test
    fun givenOnlyBackgroundRestrictionRows_whenTogglingBackgroundCard_thenAllRowsAndStatusFollow() = runTest {
        val screenOff = detailMethod(
            OwnWakeKind.SCREEN_OFF_RUN,
            detailPath("screen", method = WakeBlockMethod.BACKGROUND_RESTRICTION, component = null, screenOff = 300),
        )
        methods = listOf(timed, screenOff)
        observe()
        assertThat(current.status).isEqualTo(WakeAppStatus.KeepAlive)

        viewModel.setBackgroundAllowed(false)
        runCurrent()

        assertThat(current.background?.allowed).isFalse()
        assertThat(current.methods.map { it.blocked }).containsExactly(true, true)
        assertThat(current.status).isEqualTo(WakeAppStatus.AllBlocked)
        assertThat(current.canBlockAll).isFalse()

        viewModel.setBackgroundAllowed(true)
        runCurrent()

        assertThat(current.background?.allowed).isTrue()
        assertThat(current.methods.map { it.blocked }).containsExactly(false, false)
        assertThat(current.status).isEqualTo(WakeAppStatus.KeepAlive)
        assertThat(background.writes).containsExactly(appKey to false, appKey to true).inOrder()
        assertThat(background.reads).isEmpty()
    }

    @Test
    fun givenLiveBackgroundUsageBecomesProtected_whenInvokingStaleRowActions_thenSkipsWrites() = runTest {
        methods = listOf(timed)
        observe()
        val key = current.methods.single().key
        val protected = BackgroundUsage(allowed = true, changeable = false)
        background.sendUsage(appKey, protected)
        runCurrent()
        assertThat(current.background).isEqualTo(protected)
        assertThat(current.canBlockAll).isFalse()

        viewModel.toggle(key, allowed = false)
        runCurrent()
        viewModel.blockAll(setOf(key, BACKGROUND_CHOICE))
        runCurrent()

        assertThat(background.writes).isEmpty()
        assertThat(background.reads).isEmpty()
    }

    @Test
    fun givenPartialBlock_whenUndoing_thenRevertsSuccessfulItemsAndReportsCounts() = runTest {
        manual.excluded = setOf("self")
        observe()
        viewModel.blockAll(current.methods.mapTo(mutableSetOf()) { it.key })
        runCurrent()
        val notice = requireNotNull(current.notice)
        assertThat(notice.text).isEqualTo(blockFailureText(null, 1, 1))
        assertThat(current.methods.map { it.blocked }).containsExactly(true, false).inOrder()

        viewModel.undo(notice.id)
        runCurrent()
        assertThat(manual.blocks.value).isEmpty()
        assertThat(background.writes).isEmpty()
    }

    @Test
    fun givenBatchUndoNotice_whenTogglingAnotherPath_thenOldUndoCannotRun() = runTest {
        observe()
        viewModel.blockAll(setOf(current.methods.first().key))
        runCurrent()
        val notice = requireNotNull(current.notice)
        assertThat(notice.undo).isNotNull()

        viewModel.toggle(current.methods.last().key, allowed = false)
        viewModel.undo(notice.id)
        runCurrent()

        assertThat(current.notice).isNull()
        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly("restart", "self")
    }

    @Test
    fun givenBatchUndoNotice_whenLeavingDetail_thenOldUndoCannotRun() = runTest {
        observe()
        viewModel.blockAll(setOf(current.methods.first().key))
        runCurrent()
        val notice = requireNotNull(current.notice)
        assertThat(notice.undo).isNotNull()

        viewModel.leaveScreen()
        viewModel.undo(notice.id)
        runCurrent()

        assertThat(current.notice).isNull()
        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly("restart")
    }

    @Test
    fun givenBlockStillRunning_whenLeavingDetail_thenShowsLiveRowsAndDiscardsLateUndo() = runTest {
        val finish = CompletableDeferred<Unit>()
        finishBlock = finish
        observe()
        viewModel.blockAll(setOf(current.methods.first().key))
        runCurrent()
        assertThat(current.methods.first().blocked).isTrue()
        assertThat(current.busy).isTrue()
        assertThat(current.notice).isNull()

        viewModel.leaveScreen()
        finish.complete(Unit)
        runCurrent()

        assertThat(current.busy).isFalse()
        assertThat(current.notice).isNull()
        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly("restart")
    }

    @Test
    fun givenBatchUndoNotice_whenUndoFails_thenShowsUndoFailure() = runTest {
        observe()
        viewModel.blockAll(current.methods.mapTo(mutableSetOf()) { it.key })
        runCurrent()
        val notice = requireNotNull(current.notice)
        manual.excluded = setOf("restart")

        viewModel.undo(notice.id)
        runCurrent()

        assertThat(current.notice?.text).isEqualTo(WakeText.Resource(string.feature_wake_api_undo_failed))
        assertThat(current.notice?.undo).isNull()
        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly("restart")
    }

    @Test
    fun givenBackgroundPermission_whenChanged_thenOnlyRereadsAndNotifiesOnRefusal() = runTest {
        observe()
        viewModel.setBackgroundAllowed(false)
        runCurrent()
        assertThat(current.background?.allowed).isFalse()
        assertThat(current.notice).isNull()
        assertThat(background.reads).isEmpty()

        background.writeFailure = BackgroundUsageFailure.NOT_CHANGEABLE
        viewModel.setBackgroundAllowed(true)
        runCurrent()
        assertThat(current.background?.allowed).isFalse()
        assertThat(current.notice?.text).isEqualTo(WakeText.Resource(string.feature_wake_api_background_not_changeable))
        assertThat(background.reads).containsExactly(appKey)
    }

    @Test
    fun givenUserAndAutomaticBlocks_whenRestoringThenUndoing_thenUsesRestoreToken() = runTest {
        manual.blocks.value = listOf(first.record())
        automatic.blocks.value = listOf(known.copy(active = true, allowed = false))
        background.sendUsage(appKey, BackgroundUsage(false, true))
        observe()

        viewModel.restoreAll()
        runCurrent()
        val notice = requireNotNull(current.notice)
        assertThat(notice.text).isEqualTo(WakeText.Resource(string.feature_wake_api_restored, DETAIL_PACKAGE))
        assertThat(manual.blocks.value).isEmpty()
        assertThat(automatic.allowedIds).containsExactly("known")
        assertThat(current.background?.allowed).isTrue()

        viewModel.undo(notice.id)
        runCurrent()
        assertThat(manual.blocks.value).containsExactly(first.record())
        assertThat(automatic.allowedIds).isEmpty()
        assertThat(current.background?.allowed).isFalse()
        assertThat(background.reads).containsExactly(appKey)
    }

    @Test
    fun givenFailedToggleAndRestore_whenReporting_thenUsesComponentAndBackgroundReasons() = runTest {
        manual.excluded = setOf("restart")
        observe()
        viewModel.toggle(current.methods.first().key, allowed = false)
        runCurrent()
        assertThat(current.notice?.text).isEqualTo(blockFailureText(null, 0, 1, "Service"))

        background.sendUsage(appKey, BackgroundUsage(false, false))
        background.writeFailure = BackgroundUsageFailure.NOT_CHANGEABLE
        viewModel.restoreAll()
        runCurrent()
        assertThat(current.notice?.text).isEqualTo(WakeText.Resource(string.feature_wake_api_background_not_changeable))
    }

    private val current get() = viewModel.uiState.value

    private fun TestScope.observe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
    }

    private fun WakePath.record() = WakeBlock(pathKey, target, blockMethod, componentType, actor, component, action, 100)
}
