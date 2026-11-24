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

package com.merxury.blocker.feature.search.impl

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
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
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.control.ControlMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Components chosen across apps change in one batch that ends with one summary. */
class SearchSelectionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fixture: SearchFixture
    private val viewModel get() = fixture.viewModel

    private val pushA = id("com.a", "PushReceiver")
    private val pushService = id("com.a", "PushService")
    private val pushB = id("com.b", "PushReceiver")
    private val appA = AppKey("com.a", OWNER)
    private val appB = AppKey("com.b", OWNER)

    @Before
    fun setUp() {
        fixture = SearchFixture(mainDispatcherRule.testDispatcher)
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("com.b", label = "B")))
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "PushReceiver"),
                component("com.a", "PushService", type = ComponentType.SERVICE),
                component("com.b", "PushReceiver"),
            ),
        )
    }

    private fun TestScope.showPush() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.search("push")
        advanceTimeBy(301)
        runCurrent()
        viewModel.selectTab(SearchTab.COMPONENTS)
    }

    private fun summary(): ControlMessage.BatchDone = (viewModel.uiState.value.messages.single().message as SearchMessage.Control).message as ControlMessage.BatchDone

    @Test
    fun givenResults_whenAComponentIsLongPressed_thenSelectionModeStartsWithItsApp() = runTest {
        showPush()

        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appB)

        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isTrue()
        assertThat(state.selected).containsExactly(pushB)
        assertThat(state.selectedAppCount).isEqualTo(1)
    }

    @Test
    fun givenSelectionMode_whenAnAppIsToggled_thenAllItsComponentsAreSelectedThenNone() = runTest {
        showPush()
        viewModel.setSelectionMode(true)

        viewModel.toggleSelectedApp(appA)
        assertThat(viewModel.uiState.value.selected).containsExactly(pushA, pushService)
        viewModel.toggleSelectedApp(appA)

        assertThat(viewModel.uiState.value.selected).isEmpty()
    }

    @Test
    fun givenSelectionMode_whenAllAreSelected_thenEveryComponentFoundAcrossAppsIsSelectedThenNone() = runTest {
        showPush()
        viewModel.setSelectionMode(true)

        viewModel.toggleSelectAll()
        assertThat(viewModel.uiState.value.selected).containsExactly(pushA, pushService, pushB)
        assertThat(viewModel.uiState.value.selectedAppCount).isEqualTo(2)
        viewModel.toggleSelectAll()

        assertThat(viewModel.uiState.value.selected).isEmpty()
    }

    @Test
    fun givenSelection_whenAnotherTabIsShown_thenSelectionModeEnds() = runTest {
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)

        viewModel.selectTab(SearchTab.APPS)

        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        assertThat(viewModel.uiState.value.selected).isEmpty()
    }

    @Test
    fun givenSelectionAcrossTwoApps_whenBlocked_thenOneBatchThroughTheControllerLayersAndOneSummary() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW_PLUS_PM))
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)
        viewModel.toggleSelectedApp(appB)

        viewModel.blockSelected()
        runCurrent()

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.BLOCK)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(request.components.map { it.id }).containsExactly(pushA, pushService, pushB)
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        val availability = fixture.components.allComponents(OWNER).first().map { it.availability }
        assertThat(availability.all { it is Availability.Blocked }).isTrue()
        assertThat(fixture.analytics.hasLogged(AnalyticsEvent("search_screen_batch_operation_performed", listOf(Param("new_state", "false"))))).isTrue()
    }

    @Test
    fun givenBlockedSelection_whenEnabled_thenEveryLayerIsUnblocked() = runTest {
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "PushReceiver", pm = PmSetting.DISABLED),
                component("com.b", "PushReceiver", pm = PmSetting.DISABLED, ifw = IfwBlock.BLOCKED, enabledInManifest = false),
            ),
        )
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectAll()

        viewModel.enableSelected()
        runCurrent()

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.UNBLOCK)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenComponentsFail_whenBlocked_thenTheSummaryCountsThem() = runTest {
        fixture.components.controlEvents = { request ->
            val outcomes = request.components.map { declaration ->
                ComponentOutcome(
                    key = ComponentKey(declaration.id, request.user),
                    state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
                    result = ControlResult.FAILED,
                    failures = mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("shell")),
                )
            }
            outcomes.groupBy { it.key.id.packageName }.map { (packageName, done) -> ControlEvent.PackageDone(packageName, done) } +
                ControlEvent.Finished(achieved = 0, partial = 0, failed = outcomes.size)
        }
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)
        viewModel.toggleSelectedApp(appB)

        viewModel.blockSelected()
        runCurrent()

        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 3))
    }

    @Test
    fun givenBatchRunning_whenShown_thenItsProgressShowsAndTheActionsWait() = runTest {
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)
        // Reserved but not sent, as while the cache is being read.
        val run = fixture.batches.reserve(BatchKey.Search(OWNER), ControlGoal.BLOCK, listOf(pushA))!!
        runCurrent()

        assertThat(viewModel.uiState.value.batch).isNotNull()
        assertThat(viewModel.uiState.value.selectionActionsEnabled).isFalse()
        viewModel.blockSelected()
        runCurrent()
        assertThat(fixture.components.requests).isEmpty()

        fixture.batches.release(run)
        runCurrent()
        assertThat(viewModel.uiState.value.batch).isNull()
    }

    @Test
    fun givenBatchSent_whenTheScreenGoesAway_thenTheSystemEndsFullyChanged() = runTest {
        fixture.control.gate = CompletableDeferred()
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectAll()
        viewModel.blockSelected()
        runCurrent()
        assertThat(fixture.batches.current(BatchKey.Search(OWNER))).isNotNull()

        // As when the view model is cleared while the request is on its way.
        viewModel.viewModelScope.cancel()
        fixture.control.gate.complete(Unit)
        runCurrent()

        assertThat(fixture.batches.current(BatchKey.Search(OWNER))).isNull()
        val availability = fixture.components.allComponents(OWNER).first().map { it.availability }
        assertThat(availability.all { it is Availability.Blocked }).isTrue()
    }

    @Test
    fun givenTwoUsers_whenAnotherUserIsSelected_thenTheSelectionEnds() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)

        viewModel.selectUser(WORK)

        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        assertThat(viewModel.uiState.value.selected).isEmpty()
    }

    /** Selects the work copy of a component, with the same package also installed for the owner. */
    private fun TestScope.selectInWork(): ComponentId {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("com.a", WORK, "A")))
        fixture.components.sendComponents(WORK, listOf(component("com.a", "PushReceiver")))
        showPush()
        viewModel.selectUser(WORK)
        runCurrent()
        viewModel.selectTab(SearchTab.COMPONENTS)
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(AppKey("com.a", WORK))
        assertThat(viewModel.uiState.value.selected).containsExactly(pushA)
        return pushA
    }

    @Test
    fun givenWorkSelection_whenTheUserFallsBackOnItsOwn_thenTheSelectionEndsAndBlockChangesNothing() = runTest {
        selectInWork()

        // As when the channel is lost: the shared selection falls back to the own user by itself.
        fixture.selectedUsers.sendUsers(listOf(ownerUser))
        runCurrent()
        val state = viewModel.uiState.value
        viewModel.blockSelected()
        runCurrent()

        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.selected).isEmpty()
        assertThat(state.selectedAppCount).isEqualTo(0)
        assertThat(fixture.components.requests).isEmpty()
    }

    @Test
    fun givenWorkSelection_whenAnotherScreenSelectsTheOwner_thenBlockChangesNothing() = runTest {
        selectInWork()

        fixture.selectedUsers.select(OWNER)
        runCurrent()
        viewModel.blockSelected()
        runCurrent()

        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        assertThat(fixture.components.requests).isEmpty()
    }

    @Test
    fun givenWorkSelectionEnded_whenWorkIsSelectedAgain_thenTheOldSelectionDoesNotReturn() = runTest {
        selectInWork()
        fixture.selectedUsers.select(OWNER)
        runCurrent()

        viewModel.selectUser(WORK)
        runCurrent()

        assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        assertThat(viewModel.uiState.value.selected).isEmpty()
    }

    @Test
    fun givenComponentGoneBeforeTheBatch_whenBlocked_thenCountedAsNotChanged() = runTest {
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)
        viewModel.toggleSelectedApp(appB)
        // B's component goes while A's stay.
        fixture.components.sendComponents(OWNER, listOf(component("com.a", "PushReceiver"), component("com.a", "PushService", type = ComponentType.SERVICE)))
        runCurrent()

        viewModel.blockSelected()
        runCurrent()

        assertThat(fixture.components.requests.single().components.map { it.id }).containsExactly(pushA, pushService)
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 1))
    }

    @Test
    fun givenAppDetailBatchChangingASelectedComponent_whenShown_thenTheSelectionWaits() = runTest {
        showPush()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelectedApp(appA)
        assertThat(viewModel.uiState.value.selectionActionsEnabled).isTrue()

        val run = fixture.batches.reserve(BatchKey.App(AppKey("com.a", OWNER)), ControlGoal.BLOCK, listOf(pushA))!!
        runCurrent()

        assertThat(viewModel.uiState.value.selectionActionsEnabled).isFalse()
        fixture.batches.release(run)
        runCurrent()
        assertThat(viewModel.uiState.value.selectionActionsEnabled).isTrue()
    }
}
