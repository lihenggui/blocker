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

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.model.system.AppActionFailure
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.applist.AppActionFailed
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The actions on an app found behave as in the app list, for the app's own user. */
class SearchAppActionsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fixture: SearchFixture
    private val viewModel get() = fixture.viewModel
    private val workApp = AppKey("com.a", WORK)

    @Before
    fun setUp() {
        fixture = SearchFixture(mainDispatcherRule.testDispatcher)
    }

    private fun TestScope.showWorkApp() {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp("com.a", WORK, "A"), installedApp("com.a", OWNER, "A")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.selectUser(WORK)
        viewModel.search("A")
        advanceTimeBy(301)
        runCurrent()
    }

    private fun failure(): AppActionFailed? = (viewModel.uiState.value.messages.lastOrNull()?.message as SearchMessage.AppFailed?)?.failure

    @Test
    fun givenEnabledApp_whenDisabled_thenTheCachedAppOfItsUserShowsDisabled() = runTest {
        showWorkApp()

        viewModel.perform(AppAction.DISABLE, workApp)

        assertThat(fixture.installedApps.calls).contains("setEnabled $workApp false")
        assertThat(fixture.loaded().apps.single().isDisabled).isTrue()
        assertThat(failure()).isNull()
        assertThat(fixture.analytics.hasLogged(AnalyticsEvent("search_screen_disable_app_clicked"))).isTrue()
    }

    @Test
    fun givenRunningApp_whenForceStopped_thenTheRunningBadgeIsReadAgain() = runTest {
        fixture.appControl.running[WORK] = mutableSetOf("com.a")
        showWorkApp()
        viewModel.perform(AppAction.CLEAR_CACHE, workApp)
        assertThat(fixture.loaded().apps.single().isRunning).isTrue()

        viewModel.perform(AppAction.FORCE_STOP, workApp)

        assertThat(fixture.appControl.calls).contains("forceStop $workApp")
        assertThat(fixture.loaded().apps.single().isRunning).isFalse()
    }

    @Test
    fun givenApp_whenDataClearedOrUninstalled_thenActedOnForItsUser() = runTest {
        showWorkApp()

        viewModel.perform(AppAction.CLEAR_DATA, workApp)
        viewModel.perform(AppAction.UNINSTALL, workApp)

        assertThat(fixture.appControl.calls).contains("clearData $workApp")
        assertThat(fixture.installedApps.calls).contains("uninstall $workApp")
        assertThat(fixture.loaded().apps).isEmpty()
    }

    @Test
    fun givenNoChannel_whenActionFails_thenAskedToGrantAccess() = runTest {
        showWorkApp()
        fixture.appControl.failures["forceStop"] = CallFailure.ChannelUnavailable

        viewModel.perform(AppAction.FORCE_STOP, workApp)

        assertThat(failure()).isEqualTo(AppActionFailed(AppAction.FORCE_STOP, "A", AppActionFailure.NoAccess))
    }

    @Test
    fun givenShizukuAsShell_whenNotPermitted_thenNeedsRoot() = runTest {
        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        showWorkApp()
        fixture.appControl.failures["clearCache"] = CallFailure.NotPermitted("The shell may not clear the cache of other apps")

        viewModel.perform(AppAction.CLEAR_CACHE, workApp)

        assertThat(failure()).isEqualTo(AppActionFailed(AppAction.CLEAR_CACHE, "A", AppActionFailure.NeedsRoot))
    }

    @Test
    fun givenRoot_whenNotPermitted_thenNotPermittedInThisMode() = runTest {
        showWorkApp()
        fixture.appControl.failures["clearData"] = CallFailure.NotPermitted("Device admin")

        viewModel.perform(AppAction.CLEAR_DATA, workApp)

        assertThat(failure()).isEqualTo(AppActionFailed(AppAction.CLEAR_DATA, "A", AppActionFailure.NotPermitted))
    }

    @Test
    fun givenAppGone_whenActionFails_thenNotFound() = runTest {
        showWorkApp()
        fixture.installedApps.callFailure = CallFailure.TargetNotFound("Not installed")

        viewModel.perform(AppAction.UNINSTALL, workApp)

        assertThat(failure()).isEqualTo(AppActionFailed(AppAction.UNINSTALL, "A", AppActionFailure.NotFound))
    }

    @Test
    fun givenSystemFailure_whenActionFails_thenTheSystemMessageIsTold() = runTest {
        showWorkApp()
        fixture.installedApps.callFailure = CallFailure.SystemError("Binder died")

        viewModel.perform(AppAction.ENABLE, workApp)

        assertThat(failure()).isEqualTo(AppActionFailed(AppAction.ENABLE, "A", AppActionFailure.SystemError("Binder died")))
    }

    @Test
    fun givenTwoFailures_whenTheFirstIsShown_thenOnlyTheSecondWaits() = runTest {
        showWorkApp()
        fixture.installedApps.callFailure = CallFailure.SystemError(null)
        viewModel.perform(AppAction.ENABLE, workApp)
        viewModel.perform(AppAction.ENABLE, workApp)
        val messages = viewModel.uiState.value.messages
        assertThat(messages).hasSize(2)

        viewModel.onMessageShown(messages.first().id)

        assertThat(viewModel.uiState.value.messages).containsExactly(messages.last())
    }
}
