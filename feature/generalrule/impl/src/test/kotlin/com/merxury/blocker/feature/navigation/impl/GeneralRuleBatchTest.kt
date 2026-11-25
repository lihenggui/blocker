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

package com.merxury.blocker.feature.navigation.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.data.device.RefreshProgress
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
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.feature.generalrule.impl.GeneralRuleUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Block all and enable all of the rules list change every matched component of the selected user in one batch. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralRuleBatchTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var fixture: GeneralRuleFixture
    private val viewModel get() = fixture.viewModel

    private val trackerA = ComponentId("com.a", "com.tracker.A")
    private val adsA = ComponentId("com.a", "com.ads.A")
    private val trackerB = ComponentId("com.b", "com.tracker.B")

    @Before
    fun setUp() {
        fixture = GeneralRuleFixture(mainDispatcherRule.testDispatcher, tempFolder.newFolder())
        fixture.rules.sendRuleList(listOf(rule(1, "com.tracker."), rule(2, "com.ads."), rule(3, "com.tracker.A")))
        fixture.installedApps.sendApps(listOf(installedApp("com.a"), installedApp("com.b"), installedApp(SELF_PACKAGE), installedApp("com.w", WORK)))
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "com.tracker.A"),
                component("com.a", "com.ads.A"),
                component("com.a", "com.other.A"),
                component("com.b", "com.tracker.B"),
                component(SELF_PACKAGE, "com.tracker.Self"),
            ),
        )
        fixture.components.sendComponents(WORK, listOf(component("com.w", "com.tracker.W")))
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
    }

    private fun TestScope.show() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }
        settle()
    }

    private fun summary(): ControlMessage = viewModel.uiState.value.messages.single().message

    private fun unavailableChannel() {
        fixture.components.controlEvents = { request ->
            val outcomes = request.components.map { declaration ->
                ComponentOutcome(
                    key = ComponentKey(declaration.id, request.user),
                    state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
                    result = ControlResult.FAILED,
                    failures = mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable),
                )
            }
            outcomes.groupBy { it.key.id.packageName }.map { (packageName, done) -> ControlEvent.PackageDone(packageName, done) } +
                ControlEvent.Finished(achieved = 0, partial = 0, failed = outcomes.size)
        }
    }

    @Test
    fun givenMatches_whenBlockAll_thenOneBatchOfEveryMatchedComponentThroughTheControllerLayers() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW_PLUS_PM))
        show()

        viewModel.controlAllComponents(enable = false)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.user).isEqualTo(OWNER)
        assertThat(request.goal).isEqualTo(ControlGoal.BLOCK)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        // Each once, though two rules match com.tracker.A; Blocker's own component is never sent.
        assertThat(request.components.map { it.id }).containsExactly(trackerA, adsA, trackerB)
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(fixture.analytics.hasLogged(AnalyticsEvent("general_rule_control_all_sdks_clicked", listOf(Param("new_state", "false"))))).isTrue()
    }

    @Test
    fun givenManifestDisabledMatch_whenEnableAll_thenItIsEnabledThroughEveryLayer() = runTest {
        val status = component("com.a", "com.tracker.A", pm = PmSetting.DISABLED, ifw = IfwBlock.BLOCKED)
        fixture.components.sendComponents(OWNER, listOf(status.copy(declaration = status.declaration.copy(enabledInManifest = false))))
        show()

        viewModel.controlAllComponents(enable = true)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.goal).isEqualTo(ControlGoal.ENABLE)
        assertThat(request.layers).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
        assertThat(request.components.map { it.id }).containsExactly(trackerA)
        assertThat(fixture.components.component(ComponentKey(trackerA, OWNER)).first()?.isSwitchOn).isTrue()
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenWorkProfileSelected_whenBlockAll_thenOnlyItsComponentsChange() = runTest {
        show()
        fixture.selectedUsers.select(WORK)
        settle()

        viewModel.controlAllComponents(enable = false)
        settle()

        val request = fixture.components.requests.single()
        assertThat(request.user).isEqualTo(WORK)
        assertThat(request.components.map { it.id }).containsExactly(ComponentId("com.w", "com.tracker.W"))
    }

    @Test
    fun givenBatchRunning_whenShown_thenProgressShowsAndAnotherBatchWaits() = runTest {
        fixture.control.gate = CompletableDeferred()
        show()

        viewModel.controlAllComponents(enable = false)
        settle()
        val progress = viewModel.uiState.value.batch!!
        assertThat(progress.isEnabling).isFalse()
        // As before, the count of what is done, 0 until the request ends.
        assertThat(progress.isIndeterminate).isFalse()
        assertThat(progress.current).isEqualTo(0)
        assertThat(progress.total).isEqualTo(3)
        viewModel.controlAllComponents(enable = true)
        settle()

        fixture.control.gate.complete(Unit)
        settle()
        assertThat(fixture.components.requests.map { it.goal }).containsExactly(ControlGoal.BLOCK)
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
    }

    @Test
    fun givenComponentsInFlightElsewhere_whenBlockAll_thenTheyAreLeftOut() = runTest {
        show()
        val search = fixture.batches.reserve(BatchKey.Search(OWNER), ControlGoal.BLOCK, listOf(trackerB))!!

        viewModel.controlAllComponents(enable = false)
        settle()

        assertThat(fixture.components.requests.single().components.map { it.id }).containsExactly(trackerA, adsA)
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
        fixture.batches.release(search)
    }

    @Test
    fun givenAllMatchesInFlightElsewhere_whenBlockAll_thenNothingIsSentOrReported() = runTest {
        show()
        val search = fixture.batches.reserve(BatchKey.Search(OWNER), ControlGoal.ENABLE, listOf(trackerA, adsA, trackerB))!!

        viewModel.controlAllComponents(enable = false)
        settle()

        assertThat(fixture.batches.current(BatchKey.Search(OWNER))).isSameInstanceAs(search)
        assertThat(fixture.components.requests).isEmpty()
        assertThat(viewModel.uiState.value.messages).isEmpty()
        fixture.batches.release(search)
    }

    @Test
    fun givenNoAccess_whenBlockAllFails_thenTheSummaryCountsThemAndOffersAccess() = runTest {
        unavailableChannel()
        show()

        viewModel.controlAllComponents(enable = false)
        settle()

        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 3, needsAccess = true))
        val shown = viewModel.uiState.value.messages.single()
        viewModel.onMessageShown(shown.id)
        settle()
        assertThat(viewModel.uiState.value.messages).isEmpty()
    }

    @Test
    fun givenNothingMatched_whenBlockAll_thenNothingIsSent() = runTest {
        fixture.rules.sendRuleList(listOf(rule(1, "com.absent.")))
        show()

        viewModel.controlAllComponents(enable = false)
        settle()

        assertThat(fixture.components.requests).isEmpty()
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(summary()).isEqualTo(ControlMessage.NoComponents)
    }

    @Test
    fun givenTheAppsAreBeingRead_whenBlockAllConfirmed_thenWaitsAndChangesTheMatches() = runTest {
        val progress = MutableSharedFlow<RefreshProgress>()
        fixture.components.refreshAllProgress = { progress }
        show()
        progress.emit(RefreshProgress.Refreshing(done = 1, total = 3, current = "com.a"))
        settle()

        viewModel.uiState.test {
            val indexing = awaitItem().rules as GeneralRuleUiState.Success
            assertThat(indexing.matchProgress).isWithin(0.001F).of(1F / 3)
            assertThat(indexing.batchActionsEnabled).isFalse()

            // A confirmation dialog may have been opened before the rebuild started.
            viewModel.controlAllComponents(enable = false)
            settle()
            assertThat(fixture.components.requests).isEmpty()
            assertThat(expectMostRecentItem().batch).isNotNull()

            fixture.components.sendComponents(
                OWNER,
                fixture.components.allComponents(OWNER).first() + component("com.b", "com.tracker.New"),
            )
            progress.emit(RefreshProgress.Finished(total = 3, failed = 1, appListFailure = null))
            settle()

            val completed = expectMostRecentItem()
            val rules = completed.rules as GeneralRuleUiState.Success
            assertThat(rules.matchProgress).isEqualTo(1F)
            assertThat(rules.batchActionsEnabled).isTrue()
            assertThat(fixture.components.requests.single().components.map { it.id }).containsExactly(trackerA, adsA, trackerB)
            assertThat(completed.messages.single().message).isEqualTo(ControlMessage.BatchDone(notChanged = 0))
        }
    }

    @Test
    fun givenAShownMatchVanishesDuringRebuild_whenBlockAllRuns_thenTheSummaryCountsItAsNotChanged() = runTest {
        val progress = MutableSharedFlow<RefreshProgress>()
        fixture.components.refreshAllProgress = { progress }
        show()
        val search = fixture.batches.reserve(BatchKey.Search(OWNER), ControlGoal.BLOCK, listOf(trackerB))!!

        viewModel.controlAllComponents(enable = false)
        settle()
        assertThat(fixture.components.requests).isEmpty()

        fixture.rules.sendRuleList(listOf(rule(1, "com.tracker.")))
        settle()
        progress.emit(RefreshProgress.Finished(total = 3, failed = 0, appListFailure = null))
        settle()

        assertThat(fixture.components.requests.single().components.map { it.id }).containsExactly(trackerA)
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 1))
        fixture.batches.release(search)
    }

    @Test
    fun givenPartialMatchesAndFailedIndex_whenBlockAll_thenShowTheErrorAndSendNothing() = runTest {
        fixture.components.refreshAllProgress = {
            flow {
                emit(RefreshProgress.Refreshing(done = 1, total = 3, current = "com.a"))
                error("Index failed")
            }
        }
        show()

        viewModel.controlAllComponents(enable = false)
        settle()

        val state = viewModel.uiState.value
        assertThat((state.rules as GeneralRuleUiState.Error).error.title).isEqualTo("Index failed")
        assertThat(state.batch).isNull()
        assertThat(fixture.components.requests).isEmpty()
    }

    @Test
    fun givenAppListCouldNotBeRead_whenBlockAll_thenSendsShownMatchesAndOffersAccess() = runTest {
        fixture.components.refreshAllProgress = {
            flowOf(RefreshProgress.Finished(total = 0, failed = 0, appListFailure = ReadFailure.ChannelUnavailable))
        }
        unavailableChannel()
        show()
        assertThat((viewModel.uiState.value.rules as GeneralRuleUiState.Success).batchActionsEnabled).isTrue()

        viewModel.controlAllComponents(enable = false)
        settle()

        assertThat(fixture.components.requests.single().components.map { it.id }).containsExactly(trackerA, adsA, trackerB)
        assertThat(viewModel.uiState.value.batch).isNull()
        assertThat(summary()).isEqualTo(ControlMessage.BatchDone(notChanged = 3, needsAccess = true))
    }
}
