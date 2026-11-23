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

package com.merxury.blocker.feature.applist.impl

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppListActionsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Created after the rule sets the main dispatcher, which the view model's scope keeps.
    private lateinit var fixture: AppListFixture
    private val viewModel get() = fixture.viewModel

    @Before
    fun setUp() {
        fixture = AppListFixture(mainDispatcherRule.testDispatcher)
    }
    private val workApp = AppKey("com.a", WORK)

    private fun TestScope.showWorkApp() {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp("com.a", WORK, "A")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.selectUser(WORK)
    }

    private fun failure(): AppListMessage.ActionFailed? = viewModel.uiState.value.message as AppListMessage.ActionFailed?

    @Test
    fun givenEnabledApp_whenDisabled_thenCachedAppShowsDisabledForItsUser() = runTest {
        showWorkApp()

        viewModel.setEnabled(workApp, enabled = false)

        assertThat(fixture.installedApps.calls).contains("setEnabled $workApp false")
        assertThat(fixture.rows().single().isDisabled).isTrue()
        assertThat(failure()).isNull()
        assertThat(fixture.analytics.hasLogged(AnalyticsEvent("app_list_disable_app_clicked"))).isTrue()
    }

    @Test
    fun givenDisabledApp_whenEnabled_thenCachedAppShowsEnabled() = runTest {
        showWorkApp()
        viewModel.setEnabled(workApp, enabled = false)

        viewModel.setEnabled(workApp, enabled = true)

        assertThat(fixture.rows().single().isDisabled).isFalse()
    }

    @Test
    fun givenRunningApp_whenForceStopped_thenRunningBadgeReadAgain() = runTest {
        fixture.appControl.running[WORK] = mutableSetOf("com.a")
        showWorkApp()
        assertThat(fixture.rows().single().isRunning).isTrue()

        viewModel.forceStop(workApp)

        assertThat(fixture.appControl.calls).contains("forceStop $workApp")
        assertThat(fixture.rows().single().isRunning).isFalse()
    }

    @Test
    fun givenApp_whenCacheCleared_thenClearedForItsUser() = runTest {
        showWorkApp()

        viewModel.clearCache(workApp)

        assertThat(fixture.appControl.calls).contains("clearCache $workApp")
        assertThat(failure()).isNull()
    }

    @Test
    fun givenRunningApp_whenDataCleared_thenRunningBadgeReadAgain() = runTest {
        fixture.appControl.running[WORK] = mutableSetOf("com.a")
        showWorkApp()

        viewModel.clearData(workApp)

        assertThat(fixture.appControl.calls).contains("clearData $workApp")
        assertThat(fixture.rows().single().isRunning).isFalse()
    }

    @Test
    fun givenApp_whenUninstalled_thenGoneFromList() = runTest {
        showWorkApp()

        viewModel.uninstall(workApp)

        assertThat(fixture.installedApps.calls).contains("uninstall $workApp")
        assertThat(fixture.rows()).isEmpty()
    }

    @Test
    fun givenNoChannel_whenActionFails_thenAskedToGrantAccess() = runTest {
        showWorkApp()
        fixture.appControl.failures["forceStop"] = CallFailure.ChannelUnavailable

        viewModel.forceStop(workApp)

        assertThat(failure()).isEqualTo(AppListMessage.ActionFailed(AppAction.FORCE_STOP, "A", FailureReason.NoAccess))
    }

    @Test
    fun givenShizukuAsShell_whenNotPermitted_thenNeedsRoot() = runTest {
        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        showWorkApp()
        fixture.appControl.failures["clearCache"] = CallFailure.NotPermitted("The shell may not clear the cache of other apps")

        viewModel.clearCache(workApp)

        assertThat(failure()).isEqualTo(AppListMessage.ActionFailed(AppAction.CLEAR_CACHE, "A", FailureReason.NeedsRoot))
    }

    @Test
    fun givenRoot_whenNotPermitted_thenNotPermittedInThisMode() = runTest {
        showWorkApp()
        fixture.appControl.failures["clearData"] = CallFailure.NotPermitted("Device admin")

        viewModel.clearData(workApp)

        assertThat(failure()).isEqualTo(AppListMessage.ActionFailed(AppAction.CLEAR_DATA, "A", FailureReason.NotPermitted))
    }

    @Test
    fun givenAppGone_whenActionFails_thenNotFound() = runTest {
        showWorkApp()
        fixture.installedApps.callFailure = CallFailure.TargetNotFound("Not installed")

        viewModel.uninstall(workApp)

        assertThat(failure()).isEqualTo(AppListMessage.ActionFailed(AppAction.UNINSTALL, "A", FailureReason.NotFound))
    }

    @Test
    fun givenSystemFailure_whenActionFails_thenSystemMessageShown() = runTest {
        showWorkApp()
        fixture.installedApps.callFailure = CallFailure.SystemError("Binder died")

        viewModel.setEnabled(workApp, enabled = false)

        assertThat(failure()).isEqualTo(AppListMessage.ActionFailed(AppAction.DISABLE, "A", FailureReason.SystemError("Binder died")))
    }

    @Test
    fun givenFailureShown_whenMessageShown_thenCleared() = runTest {
        showWorkApp()
        fixture.installedApps.callFailure = CallFailure.SystemError(null)
        viewModel.setEnabled(workApp, enabled = true)

        viewModel.onMessageShown()

        assertThat(failure()).isNull()
    }
}
