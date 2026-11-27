// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.os.Build.VERSION_CODES
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.data.device.AppRestrictionFailure
import com.merxury.blocker.core.data.device.AppRestrictionRepository
import com.merxury.blocker.core.data.device.AppRestrictionResult
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.domain.wake.ForceStopAppUseCase
import com.merxury.blocker.core.domain.wake.ObserveAppWakeActivityUseCase
import com.merxury.blocker.core.domain.wake.ObserveAppWakeStateUseCase
import com.merxury.blocker.core.domain.wake.ObservePotentialWakeEntriesUseCase
import com.merxury.blocker.core.domain.wake.ObserveWakeAvailabilityUseCase
import com.merxury.blocker.core.domain.wake.RestoreAppWakeChangesUseCase
import com.merxury.blocker.core.domain.wake.SetWakePathsBlockedUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.PotentialWakeEntry
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.testing.controller.FakeAppControl
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
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [VERSION_CODES.P], manifest = Config.NONE)
class WakeAppViewModelTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    private val manual = FakeWakeBlockRepository()
    private val automatic = FakeAutomaticWakeBlockRepository()
    private var finishBlock: CompletableDeferred<Unit>? = null
    private val manualBatch = object : WakeBlockRepository by manual {
        override suspend fun blockRecordingChanges(requests: List<Pair<WakePath, WakeBlockMethod>>) = manual.blockRecordingChanges(requests).also { finishBlock?.await() }
    }
    private val background = FakeBackgroundUsageRepository()
    private val restriction = FakeAppRestrictionRepository()
    private var systemBackground: BackgroundUsage? = null
    private var systemRestriction: DeepBackgroundRestriction? = null
    private var finishRefresh: CompletableDeferred<Unit>? = null
    private val restrictionReads = mutableListOf<AppKey>()
    private val backgroundWithSystemReadback = object : BackgroundUsageRepository by background {
        override suspend fun read(app: AppKey): BackgroundUsageResult {
            finishRefresh?.await()
            systemBackground?.let { background.sendUsage(app, it) }
            return background.read(app)
        }
    }
    private val restrictionWithSystemReadback = object : AppRestrictionRepository by restriction {
        override suspend fun read(app: AppKey): AppRestrictionResult {
            restrictionReads += app
            systemRestriction?.let { restriction.sendRestriction(app, it) }
            return restriction.read(app)
        }
    }
    private val installedApps = FakeInstalledAppRepository()
    private val appControl = FakeAppControl()
    private var beforeForceStop: suspend (AppKey) -> Unit = {}
    private val forceStop = ForceStopAppUseCase(object : AppControl by appControl {
        override suspend fun forceStop(app: AppKey) = appControl.forceStop(app.also { beforeForceStop(it) })
    })
    private val appKey = AppKey(DETAIL_PACKAGE, UserId.SYSTEM)
    private val first = detailPath("restart")
    private val second = detailPath("self", component = "$DETAIL_PACKAGE.Sync")
    private val known = AutomaticWakeBlock("known", "peer", DETAIL_PACKAGE, "$DETAIL_PACKAGE.Known", "bind", false, true)
    private val timed = detailMethod(OwnWakeKind.TIMED, detailPath("alarm", method = WakeBlockMethod.BACKGROUND_RESTRICTION, component = null))
    private var methods = listOf(detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, first), detailMethod(OwnWakeKind.SELF_START, second))
    private val base by lazy { detailState(own = methods) }
    private val dormant = MutableStateFlow<List<PotentialWakeEntry>>(emptyList())
    private val scanner = mock<ObservePotentialWakeEntriesUseCase> {
        on { invoke(eq(DETAIL_PACKAGE), any()) } doAnswer { invocation ->
            val excluded = invocation.getArgument<Set<String>>(1)
            combine(dormant, manual.blocks) { entries, blocks ->
                entries.filterNot { it.state.path.component in excluded }.map { entry ->
                    val state = entry.state
                    entry.copy(state = state.copy(block = if (blocks.any { it.pathKey == state.path.pathKey }) WakePathBlock.USER else WakePathBlock.NONE))
                }
            }
        }
    }
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
            DETAIL_PACKAGE, state, availability, installedApps, FakeSelectedUserRepository(), details,
            backgroundWithSystemReadback, restrictionWithSystemReadback, scanner, SetWakePathsBlockedUseCase(manualBatch, automatic, background),
            forceStop, RestoreAppWakeChangesUseCase(activity, manual, automatic, background, restriction),
        )
    }

    @After
    fun clearViewModel() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun givenExternalChangesAndAnUndoNotice_whenRefreshingRestrictions_thenPublishesReadbackAndKeepsTheNotice() = runTest {
        observe()
        viewModel.blockAll(setOf(current.methods.first().key))
        runCurrent()
        val notice = requireNotNull(current.notice)
        val refreshedUsage = BackgroundUsage(allowed = false, changeable = true)
        val refreshedRestriction = DeepBackgroundRestriction(true, false, true, changeable = true)
        systemBackground = refreshedUsage
        systemRestriction = refreshedRestriction
        val finish = CompletableDeferred<Unit>()
        finishRefresh = finish

        viewModel.refreshRestrictions()
        runCurrent()

        assertThat(current.busy).isFalse()
        assertThat(current.notice).isEqualTo(notice)
        assertThat(current.background?.allowed).isTrue()
        assertThat(current.deepRestriction?.anyActive).isFalse()

        finish.complete(Unit)
        runCurrent()

        assertThat(background.reads).containsExactly(appKey)
        assertThat(restrictionReads).containsExactly(appKey)
        assertThat(current.background).isEqualTo(refreshedUsage)
        assertThat(current.deepRestriction).isEqualTo(refreshedRestriction)
        assertThat(current.busy).isFalse()
        assertThat(current.notice).isEqualTo(notice)
    }

    @Test
    fun givenAnInstalledApp_whenItsVersionChanges_thenRefreshesBothRestrictions() = runTest {
        val app = wakeTestApp(DETAIL_PACKAGE)
        installedApps.sendApps(listOf(app))
        observe()
        installedApps.sendApps(listOf(app.copy(label = "New label")))
        runCurrent()
        assertThat(background.reads).isEmpty()
        assertThat(restrictionReads).isEmpty()

        val refreshedUsage = BackgroundUsage(allowed = false, changeable = true)
        val refreshedRestriction = DeepBackgroundRestriction(true, true, true, changeable = true)
        systemBackground = refreshedUsage
        systemRestriction = refreshedRestriction
        installedApps.sendApps(listOf(app.copy(versionCode = app.versionCode + 1)))
        runCurrent()

        assertThat(background.reads).containsExactly(appKey)
        assertThat(restrictionReads).containsExactly(appKey)
        assertThat(current.background).isEqualTo(refreshedUsage)
        assertThat(current.deepRestriction).isEqualTo(refreshedRestriction)
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
    fun givenThreeRowsAndBackgroundUsage_whenBlockingThenUndoing_thenCountsFourChoicesAndRestoresThatBatch() = runTest {
        val existing = detailPath("outside").record()
        manual.blocks.value = listOf(existing)
        automatic.setBlocks(listOf(known))
        observe()
        viewModel.blockAll(current.choices.mapTo(mutableSetOf()) { it.key } - setOf(DEEP_RESTRICTION_CHOICE, FORCE_STOP_CHOICE))
        runCurrent()
        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly("outside", "restart", "self")
        assertThat(automatic.allowedIds).isEmpty()
        assertThat(current.rows.map { it.blocked }).containsExactly(true, true, true).inOrder()
        assertThat(current.background?.allowed).isFalse()
        val notice = requireNotNull(current.notice)
        assertThat(notice.text).isEqualTo(blockedCount(4))

        viewModel.undo(notice.id)
        runCurrent()
        assertThat(manual.blocks.value).containsExactly(existing)
        assertThat(automatic.allowedIds).containsExactly("known")
        assertThat(current.background?.allowed).isTrue()
        assertThat(background.writes).containsExactly(appKey to false, appKey to true).inOrder()
        assertThat(background.reads).isEmpty()
        assertThat(appControl.calls).isEmpty()
    }

    @Test
    fun givenDormantComponent_whenTogglingBothWays_thenWritesComponentBlockAndObservesBothStates() = runTest {
        val entry = potentialEntry()
        dormant.value = listOf(entry, potentialEntry(name = "Service"))
        methods = listOf(detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, first.copy(blockMethod = WakeBlockMethod.COMPONENT)))
        observe()
        val key = current.potential.single().key

        viewModel.toggle(key, allowed = false)
        runCurrent()
        assertThat(manual.blocks.value.map { it.pathKey to it.method }).containsExactly(entry.state.path.pathKey to WakeBlockMethod.COMPONENT)
        assertThat(current.potential.single().blocked).isTrue()

        viewModel.toggle(key, allowed = true)
        runCurrent()
        assertThat(manual.blocks.value).isEmpty()
        assertThat(current.potential.single().blocked).isFalse()
    }

    @Test
    fun givenDormantComponent_whenAnAutomaticPmPathAppears_thenRescansWithTheComponentExcluded() = runTest {
        dormant.value = listOf(potentialEntry(name = "Known"))
        observe()
        assertThat(current.potential).hasSize(1)

        val pm = known.copy(method = "PM")
        automatic.setBlocks(listOf(pm))
        runCurrent()

        verify(scanner).invoke(DETAIL_PACKAGE, setOf(pm.component))
        assertThat(current.potential).isEmpty()
        assertThat(current.relations.single().automatic).isEqualTo(pm)
    }

    @Test
    fun givenChangeableDeepRestriction_whenToggling_thenStateFollowsRepositoryReadback() = runTest {
        observe()
        assertThat(current.deepRestriction?.active).isFalse()

        viewModel.setDeepRestricted(true)
        runCurrent()
        assertThat(current.deepRestriction?.active).isTrue()
        assertThat(current.notice).isNull()

        viewModel.setDeepRestricted(false)
        runCurrent()
        assertThat(current.deepRestriction?.active).isFalse()
        assertThat(restriction.writes).containsExactly(appKey to true, appKey to false).inOrder()
    }

    @Test
    fun givenPartialAndLegacyDeepRestrictions_whenToggling_thenClearsPartialAndEnablesSupportedOperations() = runTest {
        val legacy = DeepBackgroundRestriction(false, false, false, changeable = true, bucketSupported = false, exactAlarmSupported = false)
        restriction.sendRestriction(appKey, legacy)
        observe()

        viewModel.setDeepRestricted(true)
        runCurrent()
        assertThat(current.deepRestriction?.active).isTrue()
        assertThat(current.notice).isNull()

        restriction.sendRestriction(appKey, DeepBackgroundRestriction(false, true, false, changeable = true))
        runCurrent()
        assertThat(current.deepRestriction?.active).isFalse()
        assertThat(current.deepRestriction?.anyActive).isTrue()
        viewModel.setDeepRestricted(false)
        runCurrent()

        assertThat(current.deepRestriction?.anyActive).isFalse()
        assertThat(current.notice).isNull()
    }

    @Test
    fun givenDeepRestrictionWriteFailures_whenToggling_thenShowsTypedNoticeAndKeepsReadback() = runTest {
        observe()
        val failures = mapOf(
            AppRestrictionFailure.NO_ACCESS to WakeBlockFailure.NoAccess,
            AppRestrictionFailure.NOT_CHANGEABLE to WakeBlockFailure.BackgroundNotChangeable,
            AppRestrictionFailure.NOT_INSTALLED to WakeBlockFailure.SystemError(null),
            AppRestrictionFailure.SYSTEM_ERROR to WakeBlockFailure.SystemError(null),
        )
        failures.forEach { (failure, mapped) ->
            restriction.writeFailure = failure
            viewModel.setDeepRestricted(true)
            runCurrent()

            assertThat(current.deepRestriction?.active).isFalse()
            assertThat(current.notice?.text).isEqualTo(blockFailureText(mapped, 0, 1))
        }
    }

    @Test
    fun givenDormantEntryAndDeepRestriction_whenBlockingWithForceStopThenUndoing_thenRestoresBothRestrictions() = runTest {
        val entry = potentialEntry()
        dormant.value = listOf(entry)
        appControl.running[appKey.userId] = mutableSetOf(DETAIL_PACKAGE)
        observe()

        viewModel.blockAll(setOf(current.potential.single().key, DEEP_RESTRICTION_CHOICE, FORCE_STOP_CHOICE))
        runCurrent()

        assertThat(manual.blocks.value.map { it.pathKey }).containsExactly(entry.state.path.pathKey)
        assertThat(current.potential.single().blocked).isTrue()
        assertThat(restriction.writes).containsExactly(appKey to true)
        assertThat(current.deepRestriction?.active).isTrue()
        assertThat(appControl.calls).containsExactly("forceStop $appKey")
        assertThat(appControl.running.getValue(appKey.userId)).isEmpty()
        val notice = requireNotNull(current.notice)
        assertThat(notice.text).isEqualTo(blockedCount(2))
        assertThat((notice.undo as WakeUndo.Block).deepRestrictionChanged).isTrue()

        viewModel.undo(notice.id)
        runCurrent()

        assertThat(manual.blocks.value).isEmpty()
        assertThat(current.potential.single().blocked).isFalse()
        assertThat(current.deepRestriction?.active).isFalse()
        assertThat(restriction.writes).containsExactly(appKey to true, appKey to false).inOrder()
    }

    @Test
    fun givenDeepRestrictionWasAlreadyActive_whenInvokingStaleBulkSelection_thenUndoLeavesItActive() = runTest {
        restriction.sendRestriction(appKey, DeepBackgroundRestriction(true, true, true, changeable = true))
        observe()
        viewModel.blockAll(setOf(current.methods.first().key, DEEP_RESTRICTION_CHOICE))
        runCurrent()

        viewModel.undo(requireNotNull(current.notice).id)
        runCurrent()

        assertThat(manual.blocks.value).isEmpty()
        assertThat(current.deepRestriction?.active).isTrue()
        assertThat(restriction.writes).isEmpty()
    }

    @Test
    fun givenStandaloneSettings_whenBlockingAndForceStopFails_thenWritesFinishFirstAndRemainUndoable() = runTest {
        methods = emptyList()
        val refused = CallFailure.SystemError("stop refused")
        appControl.failures["forceStop"] = refused
        beforeForceStop = { key ->
            assertThat(key).isEqualTo(appKey)
            assertThat(background.writes).containsExactly(appKey to false)
            assertThat(restriction.read(appKey).value.active).isTrue()
        }
        observe()
        assertThat(current.canBlockAll).isTrue()

        viewModel.blockAll(setOf(BACKGROUND_CHOICE, DEEP_RESTRICTION_CHOICE, FORCE_STOP_CHOICE))
        runCurrent()

        val notice = requireNotNull(current.notice)
        assertThat(appControl.calls).containsExactly("forceStop $appKey")
        assertThat(notice.text).isEqualTo(
            WakeText.Resource(
                string.feature_wake_api_list_pair,
                blockedCount(2),
                WakeText.Resource(string.feature_wake_api_force_stop_failed, current.app.label),
            ),
        )
        viewModel.undo(notice.id)
        runCurrent()

        assertThat(current.background?.allowed).isTrue()
        assertThat(current.deepRestriction?.anyActive).isFalse()
        assertThat(appControl.calls).hasSize(1)
    }

    @Test
    fun givenOutboundPathAndBackgroundChoice_whenBlocking_thenOnlyTheOpenAppStopsAndCountIncludesBothWrites() = runTest {
        val peer = "another.app"
        val path = detailPath("out", target = peer)
        automatic.setBlocks(listOf(AutomaticWakeBlock("outgoing", DETAIL_PACKAGE, peer, checkNotNull(path.component), "", false, true)))
        appControl.running[UserId.SYSTEM] = mutableSetOf(DETAIL_PACKAGE, peer)
        beforeForceStop = {
            assertThat(automatic.currentBlocks.single().active).isTrue()
            assertThat(background.writes).containsExactly(appKey to false)
        }
        observe()

        viewModel.blockAll(setOf(current.relations.single().key, BACKGROUND_CHOICE, FORCE_STOP_CHOICE))
        runCurrent()

        assertThat(current.notice?.text).isEqualTo(blockedCount(2))
        assertThat(appControl.calls).containsExactly("forceStop $appKey")
        assertThat(appControl.running[UserId.SYSTEM]).containsExactly(peer)
    }

    @Test
    fun givenAGroupedRowPartlyBlocks_whenForceStopIsSelected_thenTheAppStopsAfterTheSuccessfulWrite() = runTest {
        methods = listOf(detailMethod(OwnWakeKind.SELF_START, first, second))
        manual.excluded = setOf(second.pathKey)
        beforeForceStop = { assertThat(manual.blocks.value.map { it.pathKey }).containsExactly(first.pathKey) }
        observe()

        viewModel.blockAll(setOf(current.methods.single().key, FORCE_STOP_CHOICE))
        runCurrent()

        assertThat(current.methods.single().blocked).isFalse()
        assertThat(current.notice?.text).isEqualTo(blockFailureText(null, 0, 1))
        assertThat(current.notice?.undo).isNotNull()
        assertThat(appControl.calls).containsExactly("forceStop $appKey")
    }

    @Test
    fun givenOnlyForceStopSelected_whenConfirmingBulkAction_thenDoesNothing() = runTest {
        observe()
        viewModel.blockAll(setOf(FORCE_STOP_CHOICE))
        runCurrent()

        assertThat(appControl.calls).isEmpty()
        assertThat(manual.blocks.value).isEmpty()
        assertThat(restriction.writes).isEmpty()
        assertThat(current.notice).isNull()
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
        assertThat(current.choices.map { it.key }).containsExactly(DEEP_RESTRICTION_CHOICE, FORCE_STOP_CHOICE).inOrder()

        viewModel.setBackgroundAllowed(true)
        runCurrent()

        assertThat(current.background?.allowed).isTrue()
        assertThat(current.methods.map { it.blocked }).containsExactly(false, false)
        assertThat(current.status).isEqualTo(WakeAppStatus.KeepAlive)
        assertThat(background.writes).containsExactly(appKey to false, appKey to true).inOrder()
        assertThat(background.reads).isEmpty()
    }

    @Test
    fun givenBackgroundReadbackIsUnavailable_whenToggling_thenTheWriteStillReachesTheRepository() = runTest {
        methods = listOf(timed)
        observe()
        background.sendUsage(appKey, null)
        background.writeFailure = BackgroundUsageFailure.NO_ACCESS
        runCurrent()
        assertThat(current.background).isNull()

        viewModel.toggle(current.methods.single().key, allowed = false)
        runCurrent()

        assertThat(background.writes).containsExactly(appKey to false)
        assertThat(current.notice?.text).isEqualTo(blockFailureText(WakeBlockFailure.NoAccess, 0, 1))
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
        assertThat(current.choices.map { it.key }).containsExactly(DEEP_RESTRICTION_CHOICE, FORCE_STOP_CHOICE).inOrder()

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
        automatic.setBlocks(listOf(known.copy(active = true, allowed = false)))
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
