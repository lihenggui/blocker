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
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.ServiceCounts
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServiceCountsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Created after the rule sets the main dispatcher, which the view model's scope keeps.
    private lateinit var fixture: AppListFixture
    private val viewModel get() = fixture.viewModel

    @Before
    fun setUp() {
        fixture = AppListFixture(mainDispatcherRule.testDispatcher)
    }
    private val app = AppKey("com.a", OWNER)

    private fun TestScope.showApp() {
        fixture.userData.sendUserData(defaultUserData.copy(showServiceInfo = true))
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        fixture.components.sendComponents(
            OWNER,
            listOf(
                service("com.a", "Running"),
                service("com.a", "DisabledByPm", pm = PmSetting.DISABLED),
                service("com.a", "BlockedByIfw", ifw = IfwBlock.BLOCKED),
                service("com.a", "Idle"),
            ),
        )
        fixture.serviceControl.running[app] = mutableSetOf(ComponentId("com.a", "com.a.Running"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private suspend fun countNow() = viewModel.countServices(app)

    @Test
    fun givenShownApp_whenCounted_thenRunningBlockedAndTotalShown() = runTest {
        showApp()

        countNow()

        assertThat(fixture.rows().single().services).isEqualTo(ServiceCounts(running = 1, blocked = 2, total = 4))
        assertThat(fixture.components.refreshedApps).containsExactly(app)
    }

    @Test
    fun givenServiceInfoHidden_whenCounted_thenNoServiceLine() = runTest {
        showApp()
        countNow()

        fixture.userData.sendUserData(defaultUserData.copy(showServiceInfo = false))

        assertThat(fixture.rows().single().services).isNull()
    }

    @Test
    fun givenCounted_whenShownAgain_thenNotReadAgain() = runTest {
        showApp()
        countNow()

        countNow()

        assertThat(fixture.components.refreshedApps).containsExactly(app)
    }

    @Test
    fun givenRowLeavesScreen_whenBeforeTheWait_thenNothingRead() = runTest {
        showApp()
        val job = backgroundScope.launch { viewModel.countServices(app) }
        advanceTimeBy(100)

        job.cancel()
        advanceTimeBy(1_000)

        assertThat(fixture.components.refreshedApps).isEmpty()
        assertThat(fixture.serviceControl.runningRequests).isEmpty()
        assertThat(fixture.rows().single().services).isNull()
    }

    @Test
    fun givenUnreadableFirewallFile_whenCounted_thenTheServiceLineAgreesWithTheSwitches() = runTest {
        showApp()
        fixture.components.appRefreshFailures["com.a"] = ReadFailure.RulesUnreadable("com.a")
        val services = listOf(
            service("com.a", "Running", ifw = IfwBlock.UNKNOWN),
            service("com.a", "DisabledByPm", pm = PmSetting.DISABLED, ifw = IfwBlock.UNKNOWN),
            service("com.a", "DisabledByManifest", pm = PmSetting.UNKNOWN, ifw = IfwBlock.UNKNOWN, enabledInManifest = false),
        )
        fixture.components.sendComponents(OWNER, services)

        countNow()

        assertThat(services.map { it.isSwitchOn }).containsExactly(true, false, false).inOrder()
        assertThat(fixture.rows().single().services).isEqualTo(ServiceCounts(running = 1, blocked = 2, total = 3))
    }

    @Test
    fun givenFirewallNotReachable_whenCounted_thenBlockedCountsPackageManagerOnly() = runTest {
        showApp()
        fixture.components.setFirewallReadable(false)
        fixture.components.sendComponents(
            OWNER,
            listOf(
                service("com.a", "Running", ifw = IfwBlock.UNKNOWN),
                service("com.a", "DisabledByPm", pm = PmSetting.DISABLED, ifw = IfwBlock.UNKNOWN),
                service("com.a", "Idle", ifw = IfwBlock.UNKNOWN),
            ),
        )

        countNow()

        assertThat(fixture.rows().single().services).isEqualTo(ServiceCounts(running = 1, blocked = 1, total = 3))
    }

    @Test
    fun givenFirewallNotReachable_whenSettingUnknown_thenCountsTheManifestDefault() = runTest {
        showApp()
        fixture.components.setFirewallReadable(false)
        fixture.components.sendComponents(OWNER, listOf(service("com.a", "Running", pm = PmSetting.UNKNOWN, ifw = IfwBlock.UNKNOWN)))

        countNow()

        assertThat(fixture.rows().single().services).isEqualTo(ServiceCounts(running = 1, blocked = 0, total = 1))
    }

    @Test
    fun givenRunningServicesNotPermitted_whenCounted_thenNothingShown() = runTest {
        showApp()
        fixture.serviceControl.runningFailure = CallFailure.NotPermitted("Root sees running services of user 0 only")

        countNow()

        assertThat(fixture.rows().single().services).isNull()
    }

    @Test
    fun givenRefreshFails_whenCounted_thenNothingShown() = runTest {
        showApp()
        fixture.components.refreshFailure = ReadFailure.ManifestUnreadable("com.a")

        countNow()

        assertThat(fixture.rows().single().services).isNull()
        assertThat(fixture.serviceControl.runningRequests).isEmpty()
    }

    @Test
    fun givenChannelNotReady_whenCounting_thenWaitsUntilReady() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        showApp()
        val counting = launch { viewModel.countServices(app) }
        advanceTimeBy(1_000)

        assertThat(fixture.components.refreshedApps).isEmpty()
        assertThat(fixture.rows().single().services).isNull()

        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        counting.join()

        assertThat(fixture.rows().single().services).isEqualTo(ServiceCounts(running = 1, blocked = 2, total = 4))
    }

    @Test
    fun givenCounted_whenForceStopped_thenCountedAgainWhenShown() = runTest {
        showApp()
        countNow()
        fixture.serviceControl.running.clear()

        viewModel.forceStop(app)
        assertThat(fixture.rows().single().services).isNull()
        countNow()

        assertThat(fixture.rows().single().services).isEqualTo(ServiceCounts(running = 0, blocked = 2, total = 4))
    }
}
