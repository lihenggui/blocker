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
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentKey
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
    fun givenPartialOutcome_whenSwitched_thenTheFailedLayerReasonAndIdentityAreTold() = runTest {
        fixture.sendComponents(component("Main"))
        answerWith(outcome("Main", ControlResult.PARTIAL, ControlLayer.INTENT_FIREWALL to ControlFailure.RulesUnreadable))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat(messages()).containsExactly(
            ControlMessage.ComponentNotChanged(
                "Main",
                ControlGoal.BLOCK,
                partial = true,
                listOf(LayerFailure(ControlLayer.INTENT_FIREWALL, ControlFailure.RulesUnreadable)),
                identity = PrivilegedIdentity.ROOT,
            ),
        )
    }

    @Test
    fun givenShellChannel_whenRefused_thenTheMessageNamesTheShell() = runTest {
        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        fixture.sendComponents(component("Main"))
        answerWith(outcome("Main", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("shell")))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat((messages().single() as ControlMessage.ComponentNotChanged).identity).isEqualTo(PrivilegedIdentity.SHELL)
    }

    @Test
    fun givenEachFailure_whenSwitched_thenItIsTheReasonTold() = runTest {
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

        failures.forEach { failure ->
            answerWith(outcome("Main", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to failure))
            viewModel.setEnabled(id("Main"), enabled = true)
        }

        assertThat(messages().map { (it as ControlMessage.ComponentNotChanged).failures.single().failure })
            .containsExactlyElementsIn(failures).inOrder()
        assertThat(messages().map { (it as ControlMessage.ComponentNotChanged).partial }.toSet()).containsExactly(false)

        viewModel.onMessageShown(viewModel.uiState.value.messages.first().id)
        assertThat(viewModel.uiState.value.messages).hasSize(failures.size - 1)
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
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(ControlGoal.BLOCK, changed = 200, notChanged = 0, mostCommon = null, identity = PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenMoreThan200Components_whenAllUnblocked_thenStepsOf200WithProgressAndOneSummary() = runTest {
        val gated = gate()
        val names = (1..450).map { "Receiver$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.RECEIVER, pm = PmSetting.DISABLED) }.toTypedArray())
        collectState()

        viewModel.unblockAll(names.map(::id))
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.UNBLOCK, done = 0, total = 450))

        gated.permits.send(Unit)
        runCurrent()
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.UNBLOCK, done = 200, total = 450))
        assertThat(viewModel.uiState.value.inFlight).hasSize(250)
        assertThat(viewModel.uiState.value.messages).isEmpty()

        gated.permits.send(Unit)
        runCurrent()
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.UNBLOCK, done = 400, total = 450))

        gated.permits.send(Unit)
        runCurrent()
        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200, 200, 50).inOrder()
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(ControlGoal.UNBLOCK, changed = 450, notChanged = 0, mostCommon = null, identity = PrivilegedIdentity.ROOT))
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
        assertThat(messages()).containsExactly(ControlMessage.BatchDone(ControlGoal.BLOCK, changed = 50, notChanged = 0, mostCommon = null, identity = PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenEveryComponentAlreadyBlocked_whenAllBlocked_thenNothingNeededChanging() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        fixture.sendComponents(component("A", pm = PmSetting.DISABLED), component("B", pm = PmSetting.DISABLED))
        collectState()

        viewModel.blockAll(listOf(id("A"), id("B")))

        val summary = messages().single() as ControlMessage.BatchDone
        assertThat(summary.changed to summary.notChanged).isEqualTo(0 to 0)
    }

    @Test
    fun givenBatchWithPartialAndFailedOutcomes_whenDone_thenOnlyChangedAchievedCountAndTheMostCommonReasonIsNamed() = runTest {
        fixture.sendComponents(component("A", pm = PmSetting.DISABLED), component("B"), component("C"), component("D"), component("E"))
        answerWith(
            // A was unblocked; E was not blocked to begin with.
            outcome("A", ControlResult.ACHIEVED),
            outcome("B", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("shell")),
            outcome("C", ControlResult.PARTIAL, ControlLayer.INTENT_FIREWALL to ControlFailure.WriteFailed("first"), state = ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)),
            outcome("D", ControlResult.FAILED, ControlLayer.INTENT_FIREWALL to ControlFailure.WriteFailed("second")),
            outcome("E", ControlResult.ACHIEVED),
        )
        collectState()

        viewModel.unblockAll(listOf(id("A"), id("B"), id("C"), id("D"), id("E")))

        assertThat(messages()).containsExactly(
            ControlMessage.BatchDone(
                ControlGoal.UNBLOCK,
                changed = 1,
                notChanged = 3,
                mostCommon = LayerFailure(ControlLayer.INTENT_FIREWALL, ControlFailure.WriteFailed("first")),
                identity = PrivilegedIdentity.ROOT,
            ),
        )
        assertThat(fixture.components.requests.single().layers).containsExactlyElementsIn(ControlLayer.entries)
    }

    @Test
    fun givenComponentTheAppShipsDisabled_whenAllUnblocked_thenItStaysAsTheAppShipsIt() = runTest {
        fixture.sendComponents(component("Off", enabledInManifest = false), component("Blocked", pm = PmSetting.DISABLED, ifw = IfwBlock.BLOCKED))
        collectState()

        viewModel.unblockAll(listOf(id("Off"), id("Blocked")))

        val request = fixture.components.requests.single()
        assertThat(request.goal to request.layers).isEqualTo(ControlGoal.UNBLOCK to ControlLayer.entries.toSet())
        val states = fixture.components.components(OWN_APP).first().associate { it.declaration.id.simpleName to it.state }
        assertThat(states).containsExactly(
            "Off",
            ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
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
        viewModel.unblockAll(listOf(id("B")))
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
        // As when the view model is cleared.
        viewModel.viewModelScope.cancel()
        gated.releaseAll()
        runCurrent()

        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200, 200, 50).inOrder()
        assertThat(fixture.components.components(OWN_APP).first().map { it.state.pm }.toSet()).containsExactly(PmSetting.DISABLED)
        assertThat(fixture.batches.current(OWN_APP)).isNull()
        assertThat(viewModel.uiState.value.messages).isEmpty()
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

        val shownAgain = ComponentControlViewModel(fixture.selectedUsers, gated, fixture.batches, fixture.channel, fixture.userData, fixture.analytics, PACKAGE, null)
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
    fun givenUnknownState_whenBlockChosen_thenBlockedThroughTheControllerLayers() = runTest {
        fixture.sendComponents(component("Main", ifw = IfwBlock.UNKNOWN))
        collectState()

        viewModel.control(id("Main"), ControlGoal.BLOCK)

        assertThat(fixture.components.requests.single().let { it.goal to it.layers }).isEqualTo(ControlGoal.BLOCK to setOf(ControlLayer.INTENT_FIREWALL))
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
}
