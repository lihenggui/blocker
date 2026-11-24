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

package com.merxury.blocker.feature.ruledetail.impl

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.model.component.ComponentId
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
import com.merxury.blocker.core.ui.control.ControlMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The switches and the batch actions of the rule detail change the selected user's components. */
class RuleDetailControlTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fixture: RuleDetailFixture
    private val viewModel get() = fixture.viewModel

    private val pmA = id("com.a", "Pm")
    private val openA = id("com.a", "Open")
    private val openB = id("com.b", "Open")

    @Before
    fun setUp() {
        fixture = RuleDetailFixture(mainDispatcherRule.testDispatcher)
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW_PLUS_PM))
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp("com.a"), installedApp("com.b"), installedApp("com.w", WORK)))
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "Pm", pm = PmSetting.DISABLED),
                component("com.a", "Open"),
                component("com.b", "Open"),
            ),
        )
        fixture.components.sendComponents(WORK, listOf(component("com.w", "Work")))
    }

    private fun TestScope.show() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        settle()
    }

    private fun failingWith(failure: ControlFailure): (ControlRequest) -> List<ControlEvent> = { request ->
        val outcomes = request.components.map { declaration ->
            ComponentOutcome(
                key = ComponentKey(declaration.id, request.user),
                state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
                result = ControlResult.FAILED,
                failures = mapOf(ControlLayer.PACKAGE_MANAGER to failure),
            )
        }
        outcomes.groupBy { it.key.id.packageName }.map { (packageName, done) -> ControlEvent.PackageDone(packageName, done) } +
            ControlEvent.Finished(achieved = 0, partial = 0, failed = outcomes.size)
    }

    private fun enabledOf(component: ComponentId): Boolean = fixture.apps().flatMap { it.rows }.single { it.id == component }.enabled

    @Test
    fun givenSwitchOn_whenTurnedOff_thenBlocksThroughTheControllerLayersAndTheRowFollowsTheCache() = runTest {
        show()

        viewModel.setEnabled(ComponentKey(openA, OWNER), enabled = false)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.user).isEqualTo(OWNER)
        assertThat(request.goal).isEqualTo(ControlGoal.BLOCK)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(request.components.map { it.id }).containsExactly(openA)
        assertThat(enabledOf(openA)).isFalse()
        // Nothing is told when it worked.
        assertThat(viewModel.uiState.value.messages).isEmpty()
        assertThat(fixture.analytics.hasLogged(AnalyticsEvent("rule_detail_switch_component_state_clicked", listOf(Param("new_state", "false"))))).isTrue()
    }

    @Test
    fun givenSwitchOff_whenTurnedOn_thenEnablesThroughEveryLayer() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))
        show()

        viewModel.setEnabled(ComponentKey(pmA, OWNER), enabled = true)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.ENABLE)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(enabledOf(pmA)).isTrue()
    }

    @Test
    fun givenChangeInFlight_whenShown_thenItsSwitchWaitsAndTapsAreIgnored() = runTest {
        fixture.control.gate = CompletableDeferred()
        show()

        viewModel.setEnabled(ComponentKey(openA, OWNER), enabled = false)
        settle()
        assertThat(viewModel.uiState.value.inFlight).containsExactly(openA)
        assertThat(enabledOf(openA)).isTrue()
        viewModel.setEnabled(ComponentKey(openA, OWNER), enabled = false)

        fixture.control.gate.complete(Unit)
        settle()
        assertThat(fixture.components.requests).hasSize(1)
        assertThat(viewModel.uiState.value.inFlight).isEmpty()
    }

    @Test
    fun givenNotPermitted_whenTurnedOff_thenTellsItCouldNotBeBlockedWithoutTheAccessHint() = runTest {
        fixture.components.controlEvents = failingWith(ControlFailure.NotPermitted("shell"))
        show()

        viewModel.setEnabled(ComponentKey(openA, OWNER), enabled = false)
        settle()

        assertThat(viewModel.uiState.value.messages.single().message).isEqualTo(ControlMessage.ComponentNotChanged("Open", ControlGoal.BLOCK, needsAccess = false))
    }

    @Test
    fun givenNoAccess_whenTurnedOn_thenTellsItCouldNotBeEnabledAndOffersAccess() = runTest {
        fixture.components.controlEvents = failingWith(ControlFailure.ChannelUnavailable)
        show()

        viewModel.setEnabled(ComponentKey(pmA, OWNER), enabled = true)
        settle()
        val shown = viewModel.uiState.value.messages.single()
        assertThat(shown.message).isEqualTo(ControlMessage.ComponentNotChanged("Pm", ControlGoal.ENABLE, needsAccess = true))

        viewModel.grantAccess()
        viewModel.onMessageShown(shown.id)
        settle()
        assertThat(fixture.channel.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.messages).isEmpty()
    }

    @Test
    fun givenAnApp_whenBlockAll_thenOneBatchOfItsComponentsAndOneSummary() = runTest {
        show()

        viewModel.controlAllComponents(fixture.apps().first(), enable = false)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.BLOCK)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(request.components.map { it.id }).containsExactly(openA, pmA)
        assertThat(viewModel.uiState.value.messages.single().message).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
        assertThat(fixture.analytics.hasLogged(AnalyticsEvent("rule_detail_control_all_components_clicked", listOf(Param("new_state", "false"))))).isTrue()
    }

    @Test
    fun givenThePage_whenEnableAll_thenEveryMatchedComponentIsUnblockedThroughEveryLayer() = runTest {
        show()

        viewModel.controlAllComponentsInPage(enable = true)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.UNBLOCK)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(request.components.map { it.id }).containsExactly(openA, pmA, openB)
        assertThat(enabledOf(pmA)).isTrue()
        assertThat(viewModel.uiState.value.messages.single().message).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenFailures_whenBlockAll_thenTheSummaryCountsThemAndOffersAccess() = runTest {
        fixture.components.controlEvents = failingWith(ControlFailure.ChannelUnavailable)
        show()

        viewModel.controlAllComponentsInPage(enable = false)
        settle()

        assertThat(viewModel.uiState.value.messages.single().message).isEqualTo(ControlMessage.BatchDone(notChanged = 3, needsAccess = true))
    }

    @Test
    fun givenBatchRunning_whenShown_thenItsProgressShowsItsComponentsWaitAndAnotherBatchWaits() = runTest {
        fixture.control.gate = CompletableDeferred()
        show()

        viewModel.controlAllComponents(fixture.apps().first(), enable = false)
        settle()
        val progress = viewModel.uiState.value.batch!!
        assertThat(progress.isEnabling).isFalse()
        assertThat(progress.total).isEqualTo(2)
        assertThat(viewModel.uiState.value.inFlight).containsExactly(openA, pmA)
        viewModel.controlAllComponentsInPage(enable = true)
        viewModel.setEnabled(ComponentKey(openA, OWNER), enabled = true)

        fixture.control.gate.complete(Unit)
        settle()
        assertThat(fixture.components.requests.map { it.goal }).containsExactly(ControlGoal.BLOCK)
        assertThat(viewModel.uiState.value.batch).isNull()
    }

    @Test
    fun givenComponentsInABatchOfAnotherScreen_whenShown_thenTheyAreInFlight() = runTest {
        show()

        val run = fixture.batches.reserve(BatchKey.Search(OWNER), ControlGoal.BLOCK, listOf(openB))!!
        settle()

        assertThat(viewModel.uiState.value.inFlight).containsExactly(openB)
        assertThat(viewModel.uiState.value.batch).isNull()
        fixture.batches.release(run)
    }

    @Test
    fun givenWorkProfileSelected_whenTheSwitchAndBlockAllAreUsed_thenOnlyItsComponentsChange() = runTest {
        show()
        fixture.selectedUsers.select(WORK)
        settle()
        val work = id("com.w", "Work")

        viewModel.setEnabled(ComponentKey(work, WORK), enabled = false)
        settle()
        viewModel.controlAllComponents(fixture.apps().single(), enable = true)
        settle()

        assertThat(fixture.components.requests.map { it.user }).containsExactly(WORK, WORK)
        assertThat(fixture.components.requests.flatMap { it.components.map { component -> component.id } }).containsExactly(work, work)
    }

    @Test
    fun givenTheSelectionChangesOnItsOwn_whenARowOfTheOwnUserIsUsed_thenItActsOnThatUser() = runTest {
        show()
        // As when another screen selects the work profile while this one still shows the owner's rows.
        fixture.selectedUsers.select(WORK)

        viewModel.setEnabled(ComponentKey(openA, OWNER), enabled = false)
        viewModel.launchActivity(ComponentKey(openA, OWNER))
        viewModel.stopService(ComponentKey(openA, OWNER))
        settle()

        assertThat(fixture.components.requests.single().user).isEqualTo(OWNER)
        assertThat(fixture.appControl.calls.single()).contains("UserId(value=0)")
        assertThat(fixture.serviceControl.stopped.single().userId).isEqualTo(OWNER)
    }
}
