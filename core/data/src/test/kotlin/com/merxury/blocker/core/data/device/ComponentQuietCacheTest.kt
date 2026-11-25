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

package com.merxury.blocker.core.data.device

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComponentQuietCacheTest : DeviceRepositoryTestBase() {

    private val installed = installedApp(PKG)
    private val tracker = declaration("Tracker")

    @Test
    fun givenFirewallAccessLostDuringRefresh_whenFirewallIsRequired_thenKeepsAllCachedRows() = runTest {
        val repository = repositoryWithCachedBlock()
        val before = cachedRows()
        system.install(installed.copy(versionCode = 2))
        system.put(OWNER, tracker, PmSetting.DISABLED)
        files.noAccess = true
        store.clearCache()

        assertThat(repository.refreshIfReadable(installed.key, FirewallRead.REQUIRED)).isInstanceOf(ReadResult.Failure::class.java)
        assertThat(cachedRows()).isEqualTo(before)
        assertThat(system.reads("pm ")).isEqualTo(2)
    }

    @Test
    fun givenNoChannelAndColdCache_whenQuietRefresh_thenStoresLivePmWithoutFirewallRows() = runTest {
        val repository = components(backgroundScope)
        channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        files.noAccess = true
        system.install(installed, tracker)
        system.put(OWNER, tracker, PmSetting.DISABLED)

        repository.components(installed.key).test {
            assertThat(awaitItem()).isEmpty()
            assertThat(repository.refreshIfReadable(installed.key, FirewallRead.KEEP_CACHED)).isEqualTo(ReadResult.Success(Unit))
            assertThat(awaitItem().single().state).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.UNKNOWN))
        }
        assertThat(dao.observeComponents(PKG, OWNER.value).first().single().ifwBlock).isNull()
    }

    @Test
    fun givenNoChannelAndCachedFirewall_whenQuietRefresh_thenUpdatesOnlyPm() = runTest {
        val repository = repositoryWithCachedBlock()
        channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        store.clearCache()
        files.noAccess = true
        system.put(OWNER, tracker, PmSetting.DISABLED)

        assertThat(repository.refreshIfReadable(installed.key, FirewallRead.KEEP_CACHED)).isEqualTo(ReadResult.Success(Unit))
        assertThat(repository.components(installed.key).first().single().state).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED))
    }

    @Test
    fun givenRulesAlreadyInStore_whenQuietRefresh_thenUsesItsCacheAndReadsLivePm() = runTest {
        val repository = repositoryWithCachedBlock()
        files.noAccess = true
        system.put(OWNER, tracker, PmSetting.DISABLED)

        assertThat(repository.refreshIfReadable(installed.key, FirewallRead.REQUIRED)).isEqualTo(ReadResult.Success(Unit))
        assertThat(repository.components(installed.key).first().single().state).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED))
    }

    @Test
    fun givenUnreadableControlOutcome_whenPreservingKnownState_thenKeepsAllCachedRows() = runTest {
        val repository = repositoryWithCachedBlock()
        val before = cachedRows()
        componentControl.apply = {
            val outcome = ComponentOutcome(
                ComponentKey(tracker.id, OWNER),
                ControlState.UNKNOWN,
                ControlResult.FAILED,
                mapOf(INTENT_FIREWALL to ControlFailure.ChannelUnavailable),
            )
            flowOf(ControlEvent.PackageDone(PKG, listOf(outcome)))
        }
        val request = ControlRequest(OWNER, listOf(tracker), ControlGoal.UNBLOCK, setOf(INTENT_FIREWALL))

        repository.control(request, keepKnownState = true).toList()

        assertThat(cachedRows()).isEqualTo(before)
    }

    private suspend fun TestScope.repositoryWithCachedBlock(): ComponentStatusRepository {
        val repository = components(backgroundScope)
        system.install(installed, tracker)
        store.setBlocked(PKG, mapOf(tracker to true))
        assertThat(repository.refresh(installed.key)).isEqualTo(ReadResult.Success(Unit))
        assertThat(repository.components(installed.key).first().single().state.ifw).isEqualTo(IfwBlock.BLOCKED)
        return repository
    }

    private suspend fun cachedRows() = listOf(
        dao.getApp(PKG, OWNER.value),
        dao.getDeclarations(PKG),
        dao.scanMarker(PKG),
        dao.observeComponents(PKG, OWNER.value).first(),
    )
}
