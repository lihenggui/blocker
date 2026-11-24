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
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
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

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private fun outcome(name: String, result: ControlResult, vararg failures: Pair<ControlLayer, ControlFailure>) = ComponentOutcome(
        key = ComponentKey(id(name), fixture.user),
        state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
        result = result,
        failures = failures.toMap(),
    )

    private fun answerWith(vararg outcomes: ComponentOutcome) {
        fixture.components.controlEvents = { request ->
            val mine = outcomes.filter { outcome -> request.components.any { it.id == outcome.key.id } }
            listOf(ControlEvent.PackageDone(PACKAGE, mine), ControlEvent.Finished(mine.size, 0, 0))
        }
    }

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
        val gated = GatedComponents(fixture.components)
        fixture.controlStatuses = gated
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
    fun givenPartialOutcome_whenSwitched_thenTheFailedLayerAndReasonAreTold() = runTest {
        fixture.sendComponents(component("Main"))
        answerWith(outcome("Main", ControlResult.PARTIAL, ControlLayer.INTENT_FIREWALL to ControlFailure.RulesUnreadable))
        collectState()

        viewModel.setEnabled(id("Main"), enabled = false)

        assertThat(viewModel.uiState.value.messages).containsExactly(
            ControlMessage.ComponentNotChanged("Main", ControlGoal.BLOCK, partial = true, listOf(LayerFailure(ControlLayer.INTENT_FIREWALL, ControlFailure.RulesUnreadable))),
        )
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

        assertThat(viewModel.uiState.value.messages.map { (it as ControlMessage.ComponentNotChanged).failures.single().failure })
            .containsExactlyElementsIn(failures).inOrder()
        assertThat(viewModel.uiState.value.messages.map { (it as ControlMessage.ComponentNotChanged).partial }.toSet()).containsExactly(false)

        viewModel.onMessageShown(viewModel.uiState.value.messages.first())
        assertThat(viewModel.uiState.value.messages).hasSize(failures.size - 1)
    }

    @Test
    fun givenUpTo200Components_whenAllBlocked_thenOneRequestWithoutCountsAndOneSummary() = runTest {
        val gated = GatedComponents(fixture.components)
        fixture.controlStatuses = gated
        val names = (1..200).map { "Service$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.SERVICE) }.toTypedArray())
        collectState()

        viewModel.controlAll(names.map(::id), ControlGoal.BLOCK)
        assertThat(viewModel.uiState.value.batch).isEqualTo(BatchProgress(ControlGoal.BLOCK, done = null, total = 200))
        assertThat(viewModel.uiState.value.inFlight).hasSize(200)

        gated.permits.send(Unit)
        runCurrent()
        assertThat(fixture.components.requests.map { it.components.size }).containsExactly(200)
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(viewModel.uiState.value.inFlight).isEmpty()
        assertThat(viewModel.uiState.value.messages).containsExactly(ControlMessage.BatchDone(ControlGoal.BLOCK, changed = 200, notChanged = 0, mostCommon = null))
    }

    @Test
    fun givenMoreThan200Components_whenAllEnabled_thenStepsOf200WithProgressAndOneSummary() = runTest {
        val gated = GatedComponents(fixture.components)
        fixture.controlStatuses = gated
        val names = (1..450).map { "Receiver$it" }
        fixture.sendComponents(*names.map { component(it, type = ComponentType.RECEIVER, pm = PmSetting.DISABLED) }.toTypedArray())
        collectState()

        viewModel.controlAll(names.map(::id), ControlGoal.ENABLE)
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
        assertThat(viewModel.uiState.value.messages).containsExactly(ControlMessage.BatchDone(ControlGoal.ENABLE, changed = 450, notChanged = 0, mostCommon = null))
    }

    @Test
    fun givenBatchWithFailures_whenDone_thenSummaryNamesTheMostCommonReason() = runTest {
        fixture.sendComponents(component("A"), component("B"), component("C"), component("D"))
        answerWith(
            outcome("A", ControlResult.ACHIEVED),
            outcome("B", ControlResult.FAILED, ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("shell")),
            outcome("C", ControlResult.PARTIAL, ControlLayer.INTENT_FIREWALL to ControlFailure.WriteFailed("first")),
            outcome("D", ControlResult.FAILED, ControlLayer.INTENT_FIREWALL to ControlFailure.WriteFailed("second")),
        )
        collectState()

        viewModel.controlAll(listOf(id("A"), id("B"), id("C"), id("D")), ControlGoal.ENABLE)

        assertThat(viewModel.uiState.value.messages).containsExactly(
            ControlMessage.BatchDone(ControlGoal.ENABLE, changed = 1, notChanged = 3, mostCommon = LayerFailure(ControlLayer.INTENT_FIREWALL, ControlFailure.WriteFailed("first"))),
        )
        assertThat(fixture.components.requests.single().layers).containsExactlyElementsIn(ControlLayer.entries)
    }

    @Test
    fun givenComponentInFlight_whenAllBlocked_thenItIsLeftOutOfTheBatch() = runTest {
        val gated = GatedComponents(fixture.components)
        fixture.controlStatuses = gated
        fixture.sendComponents(component("A"), component("B"))
        collectState()

        viewModel.setEnabled(id("A"), enabled = false)
        viewModel.controlAll(listOf(id("A"), id("B")), ControlGoal.BLOCK)
        gated.releaseAll()

        assertThat(fixture.components.requests.map { request -> request.components.map { it.id } })
            .containsExactly(listOf(id("A")), listOf(id("B")))
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
