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

package com.merxury.blocker.feature.appdetail.impl

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.BatchRun
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.control.ControlMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComponentControlViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var fixture = AppDetailFixture(mainDispatcherRule.testDispatcher)
    private val viewModel get() = fixture.control

    private fun TestScope.collectState(viewModel: ComponentControlViewModel = this@ComponentControlViewModelTest.viewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private fun messages() = viewModel.uiState.value.messages.map { it.message }

    private fun outcome(
        name: String,
        result: ControlResult,
        vararg failures: Pair<ControlLayer, ControlFailure>,
        state: ControlState = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
    ) = ComponentOutcome(
        key = ComponentKey(id(name), fixture.user),
        state = state,
        result = result,
        failures = failures.toMap(),
    )

    private fun answerWith(vararg outcomes: ComponentOutcome) {
        fixture.components.controlEvents = { request ->
            val mine = outcomes.filter { outcome -> request.components.any { it.id == outcome.key.id } }
            listOf(ControlEvent.PackageDone(PACKAGE, mine), ControlEvent.Finished(mine.size, 0, 0))
        }
    }

    private fun gate(): GatedComponents = GatedComponents(fixture.components).also { fixture.controlStatuses = it }

    @Test
    fun givenNewViewModel_whenNothingCollects_thenNothingIsSent() = runTest {
        fixture.sendComponents(component("Main"))

        assertThat(viewModel.uiState.value).isEqualTo(ControlUiState())
        assertThat(fixture.components.requests).isEmpty()
    }

    @Test
    fun givenBlockedComponent_whenSwitchedOn_thenEnabledThroughEveryLayer() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        fixture.sendComponents(component("Main", ifw = IfwBlock.BLOCKED))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = true)

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.ENABLE)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(request.user).isEqualTo(OWNER)
        assertThat(request.components.map { it.id }).containsExactly(id("Main"))
        assertThat(viewModel.uiState.value).isEqualTo(ControlUiState())
    }

    @Test
    fun givenEachControllerType_whenSwitchedOff_thenBlockedThroughItsLayers() = runTest {
        fixture.sendComponents(component("Main"))
        collectState()
        val expected = mapOf(
            ControllerType.PM to setOf(ControlLayer.PACKAGE_MANAGER),
            ControllerType.SHIZUKU to setOf(ControlLayer.PACKAGE_MANAGER),
            ControllerType.IFW to setOf(ControlLayer.INTENT_FIREWALL),
            ControllerType.IFW_PLUS_PM to setOf(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL),
        )

        expected.keys.forEach { type ->
            fixture.userData.sendUserData(defaultUserData.copy(controllerType = type))
            viewModel.setEnabled(id("Main"), enabled = false)
        }

        assertThat(fixture.components.requests.map { it.goal }.toSet()).containsExactly(ControlGoal.BLOCK)
        assertThat(fixture.components.requests.map { it.layers }).containsExactlyElementsIn(expected.values).inOrder()
    }

    @Test
    fun givenChangeInFlight_whenSwitchedAgain_thenTheTapIsIgnoredUntilItEnds() = runTest {
        val gated = gate()
        fixture.sendComponents(component("Main"))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)
        assertThat(viewModel.uiState.value.inFlight).containsExactly(id("Main"))
        viewModel.setEnabled(id("Main"), enabled = true)

        gated.releaseAll()

        assertThat(fixture.components.requests.map { it.goal }).containsExactly(ControlGoal.BLOCK)
        assertThat(viewModel.uiState.value.inFlight).isEmpty()
    }

    @Test
    fun givenAchievedOutcome_whenSwitched_thenNoMessage() = runTest {
        fixture.sendComponents(component("Main"))
        answerWith(outcome("Main", ControlResult.ACHIEVED))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat(viewModel.uiState.value.messages).isEmpty()
    }

    @Test
    fun givenProtectedComponent_whenSwitchedOff_thenNoMessage() = runTest {
        fixture.sendComponents(component("Protected"))
        answerWith(outcome("Protected", ControlResult.SKIPPED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ProtectedComponent))
        collectState()

        viewModel.setEnabled(id("Protected"), enabled = false)

        assertThat(messages()).isEmpty()
    }

    @Test
    fun givenComponentVanished_whenSwitchedOff_thenTellsItCouldNotBeBlocked() = runTest {
        collectState()

        viewModel.setEnabled(id("Gone"), enabled = false)

        assertThat(fixture.components.requests).isEmpty()
        assertThat(viewModel.uiState.value.messages.single().message).isEqualTo(ControlMessage.ComponentNotChanged("Gone", ControlGoal.BLOCK))
    }

    @Test
    fun givenPartialOutcome_whenSwitched_thenToldAsNotChanged() = runTest {
        fixture.sendComponents(component("Main"))
        answerWith(outcome("Main", ControlResult.PARTIAL, ControlLayer.INTENT_FIREWALL to ControlFailure.RulesUnreadable))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat(messages()).containsExactly(ControlMessage.ComponentNotChanged("Main", ControlGoal.BLOCK))
    }

    @Test
    fun givenEachFailure_whenSwitched_thenOnlyMissingAccessIsOffered() = runTest {
        val failures = listOf(
            ControlFailure.ChannelUnavailable,
            ControlFailure.NotPermitted("shell"),
            ControlFailure.TargetNotFound("gone"),
            ControlFailure.RulesUnreadable,
            ControlFailure.WriteFailed("disk full"),
            ControlFailure.SystemError("binder died"),
            ControlFailure.StateNotConfirmed,
        )
        fixture.sendComponents(component("Main"))
        collectState()

        // Each told before the next tap, as the screen shows them.
        val told = failures.map { failure ->
            answerWith(outcome("Main", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to failure))
            viewModel.setEnabled(id("Main"), enabled = true)
            val queued = viewModel.uiState.value.messages.single()
            viewModel.onMessageShown(queued.id)
            queued.message as ControlMessage.ComponentNotChanged
        }

        // Only missing access is something the user can act on, so only it is offered.
        assertThat(told.map { it.needsAccess }).containsExactly(true, false, false, false, false, false, false).inOrder()
        assertThat(viewModel.uiState.value.messages).isEmpty()
    }

    @Test
    fun givenSameSwitchFailingTwice_whenBothAreQueued_thenEachIsToldApart() = runTest {
        fixture.sendComponents(component("Main"))
        answerWith(outcome("Main", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)
        viewModel.setEnabled(id("Main"), enabled = false)

        val queued = viewModel.uiState.value.messages
        assertThat(queued.map { it.message }.distinct()).hasSize(1)
        assertThat(queued.map { it.id }.distinct()).hasSize(2)

        viewModel.onMessageShown(queued.first().id)
        assertThat(viewModel.uiState.value.messages).containsExactly(queued.last())
    }

    @Test
    fun givenUpTo200Components_whenAllBlocked_thenOneRequestWithoutCountsAndOneSummary() = runTest {
        val gated = gate()
        val names = (1..200).map { "Service$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.SERVICE) }.toTypedArray())
        collectState()

        viewModel.blockAll(names.map(::id))
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.BLOCK, done = null, total = 200))
        assertThat(viewModel.uiState.value.inFlight).hasSize(200)

        gated.permits.send(Unit)
        runCurrent()
        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200)
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(viewModel.uiState.value.inFlight).isEmpty()
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenMoreThan200Components_whenAllEnabled_thenStepsOf200WithProgressAndOneSummary() = runTest {
        val gated = gate()
        val names = (1..450).map { "Receiver$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.RECEIVER, pm = PmSetting.DISABLED) }.toTypedArray())
        collectState()

        viewModel.enableAll(names.map(::id))
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.ENABLE, done = 0, total = 450))

        gated.permits.send(Unit)
        runCurrent()
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.ENABLE, done = 200, total = 450))
        assertThat(viewModel.uiState.value.inFlight).hasSize(250)
        assertThat(viewModel.uiState.value.messages).isEmpty()

        gated.permits.send(Unit)
        runCurrent()
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.ENABLE, done = 400, total = 450))

        gated.permits.send(Unit)
        runCurrent()
        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200, 200, 50).inOrder()
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenMostComponentsAlreadyBlocked_whenAllBlockedInStepsOf200_thenOnlyTheChangedAreCounted() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val names = (1..450).map { "Receiver$it" }
        // Every ninth one runs; the rest are blocked already.
        fixture.sendComponents(*names.mapIndexed { index, name -> component(name, type = ComponentType.RECEIVER, pm = if (index % 9 == 0) PmSetting.DEFAULT else PmSetting.DISABLED) }.toTypedArray())
        collectState()

        viewModel.blockAll(names.map(::id))

        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200, 200, 50).inOrder()
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenEveryComponentAlreadyBlocked_whenAllBlocked_thenTheBatchEndsWithoutFailures() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        fixture.sendComponents(component("A", pm = PmSetting.DISABLED), component("B", pm = PmSetting.DISABLED))
        collectState()

        viewModel.blockAll(listOf(id("A"), id("B")))

        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenBatchWithProtectedComponent_whenDone_thenItDoesNotCountAsNotChanged() = runTest {
        fixture.sendComponents(component("Protected"), component("Other"))
        answerWith(
            outcome("Protected", ControlResult.SKIPPED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ProtectedComponent),
            outcome("Other", ControlResult.ACHIEVED),
        )
        collectState()

        viewModel.blockAll(listOf(id("Protected"), id("Other")))

        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenBatchWithPartialAndFailedOutcomes_whenDone_thenEveryOneNotAchievedCountsAsNotChanged() = runTest {
        fixture.sendComponents(component("A", pm = PmSetting.DISABLED), component("B"), component("C"), component("D"), component("E"))
        answerWith(
            outcome("A", ControlResult.ACHIEVED),
            outcome("B", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("shell")),
            outcome("C", ControlResult.PARTIAL, ControlLayer.INTENT_FIREWALL to ControlFailure.WriteFailed("first"), state = ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)),
            outcome("D", ControlResult.FAILED, ControlLayer.INTENT_FIREWALL to ControlFailure.WriteFailed("second")),
            outcome("E", ControlResult.ACHIEVED),
        )
        collectState()

        viewModel.enableAll(listOf(id("A"), id("B"), id("C"), id("D"), id("E")))

        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 3))
        assertThat(fixture.components.requests.single().layers).containsExactlyElementsIn(ControlLayer.entries)
    }

    @Test
    fun givenManifestDisabledComponent_whenAllEnabled_thenPackageManagerEnablesIt() = runTest {
        fixture.sendComponents(component("Off", enabledInManifest = false), component("Blocked", pm = PmSetting.DISABLED, ifw = IfwBlock.BLOCKED))
        collectState()

        viewModel.enableAll(listOf(id("Off"), id("Blocked")))

        val request = fixture.components.requests.single()
        assertThat(request.goal to request.layers).isEqualTo(ControlGoal.ENABLE to ControlLayer.entries.toSet())
        val states = fixture.components.components(OWN_APP).first().associate { it.declaration.id.simpleName to it.state }
        assertThat(states).containsExactly(
            "Off",
            ControlState(PmSetting.ENABLED, IfwBlock.NOT_BLOCKED),
            "Blocked",
            ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
        )
    }

    @Test
    fun givenComponentInFlight_whenAllBlocked_thenItIsLeftOutOfTheBatch() = runTest {
        val gated = gate()
        fixture.sendComponents(component("A"), component("B"))
        collectState()

        viewModel.setEnabled(id("A"), enabled = false)
        viewModel.blockAll(listOf(id("A"), id("B")))
        gated.releaseAll()

        assertThat(fixture.components.requests.map { request -> request.components.map { it.id } })
            .containsExactly(listOf(id("A")), listOf(id("B")))
    }

    @Test
    fun givenBatchRunning_whenAnotherBatchIsChosen_thenItIsIgnored() = runTest {
        val gated = gate()
        fixture.sendComponents(component("A"), component("B"))
        collectState()

        viewModel.blockAll(listOf(id("A")))
        viewModel.enableAll(listOf(id("B")))
        viewModel.blockAll(listOf(id("B")))
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.BLOCK, done = null, total = 1))
        gated.releaseAll()

        assertThat(fixture.components.requests.map { request -> request.components.map { it.id } }).containsExactly(listOf(id("A")))
        assertThat(viewModel.uiState.value.batch).isNull()
    }

    @Test
    fun givenBatchLargerThan200_whenTheScreenGoesAway_thenItRunsToTheEndAndTellsNothing() = runTest {
        val gated = gate()
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val names = (1..450).map { "Receiver$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.RECEIVER) }.toTypedArray())
        collectState()

        viewModel.blockAll(names.map(::id))
        gated.permits.send(Unit)
        runCurrent()
        // Watched from outside the view model, which stops observing once cleared.
        val run = fixture.batches.current(BatchKey.App(OWN_APP))!!
        val seen = mutableListOf<BatchRun?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.batches.running(BatchKey.App(OWN_APP)).collect { seen += it } }
        // As when the view model is cleared.
        viewModel.viewModelScope.cancel()
        gated.releaseAll()
        runCurrent()

        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200, 200, 50).inOrder()
        assertThat(fixture.components.components(OWN_APP).first().map { it.state.pm }.toSet()).containsExactly(PmSetting.DISABLED)
        assertThat(seen).containsExactly(run, null).inOrder()
        assertThat(run.step.value.done).isEqualTo(450)
        assertThat(run.outcomes.getCompleted().map { it.result }.toSet()).containsExactly(ControlResult.ACHIEVED)
        assertThat(run.outcomes.getCompleted()).hasSize(450)
    }

    @Test
    fun givenRequestThatThrows_whenBatchRuns_thenOutcomesSoFarKeptTheRestFailedAndNothingCrashes() = runTest {
        val throwing = ThrowingOnRequest(fixture.components, failingRequest = 2)
        fixture.controlStatuses = throwing
        val names = (1..450).map { "Receiver$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.RECEIVER, pm = PmSetting.DISABLED) }.toTypedArray())
        collectState()

        viewModel.enableAll(names.map(::id))

        assertThat(throwing.requests).isEqualTo(2)
        assertThat(fixture.batches.current(BatchKey.App(OWN_APP))).isNull()
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 250))
    }

    @Test
    fun givenRequestThatThrows_whenTheScreenIsGone_thenTheBatchEndsQuietly() = runTest {
        val throwing = ThrowingOnRequest(GatedComponents(fixture.components).also { it.releaseAll() }, failingRequest = 1)
        fixture.controlStatuses = throwing
        fixture.sendComponents(component("A"))
        collectState()

        viewModel.blockAll(listOf(id("A")))
        viewModel.viewModelScope.cancel()
        runCurrent()

        assertThat(fixture.batches.current(BatchKey.App(OWN_APP))).isNull()
    }

    @Test
    fun givenBatchChosen_whenTheCacheIsStillBeingRead_thenItAlreadyRunsAndHoldsItsComponents() = runTest {
        val slowCache = SlowCache(fixture.components)
        fixture.controlStatuses = slowCache
        fixture.sendComponents(component("A"), component("B"))
        collectState()

        viewModel.blockAll(listOf(id("A"), id("B")))
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.BLOCK, done = null, total = 2))
        assertThat(viewModel.uiState.value.inFlight).containsExactly(id("A"), id("B"))
        viewModel.setEnabled(id("A"), enabled = false)
        viewModel.enableAll(listOf(id("A"), id("B")))

        slowCache.reads.send(Unit)
        runCurrent()

        assertThat(fixture.components.requests.map { request -> request.goal to request.components.map { it.id } })
            .containsExactly(ControlGoal.BLOCK to listOf(id("A"), id("B")))
        assertThat(viewModel.uiState.value.batch).isNull()
    }

    @Test
    fun givenNoAccess_whenABatchFails_thenGrantingAccessIsOffered() = runTest {
        fixture.sendComponents(component("A"), component("B"))
        answerWith(
            outcome("A", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable),
            outcome("B", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable),
        )
        collectState()

        viewModel.blockAll(listOf(id("A"), id("B")))

        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 2, needsAccess = true))
    }

    @Test
    fun givenSwitchFailingAgainAndAgain_whenQueued_thenOneMessageWaitsPerComponentAndSummariesStay() = runTest {
        fixture.sendComponents(component("Main"), component("Other"))
        answerWith(
            outcome("Main", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable),
            outcome("Other", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable),
        )
        collectState()

        repeat(20) { viewModel.setEnabled(id("Main"), enabled = false) }
        viewModel.blockAll(listOf(id("Other")))
        repeat(3) { viewModel.setEnabled(id("Main"), enabled = true) }
        viewModel.setEnabled(id("Other"), enabled = false)

        val queued = viewModel.uiState.value.messages
        assertThat(queued.map { it.component }).containsExactly(id("Main"), null, id("Main"), id("Other")).inOrder()
        assertThat((queued[2].message as ControlMessage.ComponentNotChanged).goal).isEqualTo(ControlGoal.ENABLE)
    }

    @Test
    fun givenBatchStartedOnAnEarlierScreen_whenShownAgain_thenItsProgressIsShown() = runTest {
        val gated = gate()
        val names = (1..450).map { "Receiver$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.RECEIVER) }.toTypedArray())
        collectState()
        viewModel.blockAll(names.map(::id))
        gated.permits.send(Unit)
        runCurrent()
        viewModel.viewModelScope.cancel()

        val shownAgain = ComponentControlViewModel(
            fixture.selectedUsers,
            gated,
            fixture.batches,
            ControlComponentUseCase(gated, GetControlLayersUseCase(fixture.userData)),
            GetControlLayersUseCase(fixture.userData),
            fixture.analytics,
            PACKAGE,
            null,
        )
        collectState(shownAgain)

        assertThat(shownAgain.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.BLOCK, done = 200, total = 450))
        assertThat(shownAgain.uiState.value.inFlight).hasSize(250)
        gated.releaseAll()
        runCurrent()
        assertThat(shownAgain.uiState.value.batch).isNull()
        assertThat(shownAgain.uiState.value.messages).isEmpty()
    }

    @Test
    fun givenAnotherUsersApp_whenSwitched_thenTheRequestIsForThatUser() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, userId = WORK.value)
        fixture.sendComponents(component("Main"))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat(fixture.components.requests.single().user).isEqualTo(WORK)
    }

    @Test
    fun givenUnknownFirewallState_whenSwitchedOff_thenBlockedThroughTheControllerLayers() = runTest {
        fixture.sendComponents(component("Main", ifw = IfwBlock.UNKNOWN))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat(fixture.components.requests.single().let { it.goal to it.layers }).isEqualTo(ControlGoal.BLOCK to setOf(ControlLayer.INTENT_FIREWALL))
    }

    /** Fails request number [failingRequest] with an exception the repository does not report. */
    private class ThrowingOnRequest(private val delegate: ComponentStatusRepository, private val failingRequest: Int) : ComponentStatusRepository by delegate {
        var requests = 0

        override fun control(request: ControlRequest): Flow<ControlEvent> = flow {
            requests++
            if (requests == failingRequest) throw IllegalStateException("Request $requests broke")
            emitAll(delegate.control(request))
        }
    }

    /** Holds each read of the cached components until a permit is sent. */
    private class SlowCache(private val delegate: ComponentStatusRepository) : ComponentStatusRepository by delegate {
        val reads = Channel<Unit>(Channel.UNLIMITED)

        override fun components(app: AppKey): Flow<List<ComponentStatus>> = flow {
            reads.receive()
            emitAll(delegate.components(app))
        }
    }

    /** Holds each request until a permit is sent, so a change stays in flight until then. */
    private class GatedComponents(private val delegate: ComponentStatusRepository) : ComponentStatusRepository by delegate {
        val permits = Channel<Unit>(Channel.UNLIMITED)

        fun releaseAll() {
            repeat(100) { permits.trySend(Unit) }
        }

        override fun control(request: ControlRequest): Flow<ControlEvent> = flow {
            permits.receive()
            emitAll(delegate.control(request))
        }
    }

    @Test
    fun givenSearchBatchChangingAComponent_whenShown_thenItIsInFlightAndBatchesWait() = runTest {
        fixture.sendComponents(component("A"), component("B"))
        collectState()

        val run = fixture.batches.reserve(BatchKey.Search(OWNER), ControlGoal.BLOCK, listOf(id("A")))!!
        viewModel.setEnabled(id("A"), enabled = false)

        assertThat(viewModel.uiState.value.inFlight).containsExactly(id("A"))
        assertThat(viewModel.uiState.value.batchActionsEnabled).isFalse()
        assertThat(fixture.components.requests).isEmpty()
        fixture.batches.release(run)
        assertThat(viewModel.uiState.value.inFlight).isEmpty()
        assertThat(viewModel.uiState.value.batchActionsEnabled).isTrue()
    }

    @Test
    fun givenSearchBatchOfAnotherUser_whenShown_thenNothingOfThisAppIsInFlight() = runTest {
        fixture.sendComponents(component("A"))
        collectState()

        fixture.batches.reserve(BatchKey.Search(WORK), ControlGoal.BLOCK, listOf(id("A")))

        assertThat(viewModel.uiState.value.inFlight).isEmpty()
        assertThat(viewModel.uiState.value.batchActionsEnabled).isTrue()
    }

    @Test
    fun givenComponentGoneBeforeTheBatch_whenBlocked_thenToldAsNotFound() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        fixture.sendComponents(component("A"))
        collectState()

        viewModel.blockAll(listOf(id("A"), id("Gone")))

        assertThat(messages()).containsExactly(ControlMessage.BatchDone(notChanged = 1))
        assertThat(fixture.components.requests.single().components.map { it.id }).containsExactly(id("A"))
    }
}
