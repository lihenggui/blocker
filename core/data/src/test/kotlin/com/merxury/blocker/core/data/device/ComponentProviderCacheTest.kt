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

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.provider.ComponentProvider
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ComponentProviderCacheTest.TestApp::class)
class ComponentProviderCacheTest : DeviceRepositoryTestBase() {

    interface TestEntryPoint :
        ComponentProvider.ProviderEntryPoint,
        GeneratedComponent

    class TestApp :
        Application(),
        GeneratedComponentManager<TestEntryPoint> {
        lateinit var entryPoint: TestEntryPoint

        override fun generatedComponent(): TestEntryPoint = entryPoint
    }

    private val installed = installedApp(PKG)
    private val tracker = declaration("Tracker")
    private val denied = ChannelState.Unavailable(UnavailableReason.DENIED)

    @Test
    fun givenCachedIfwBlockAndMissingAccess_whenReadingTwice_thenReturnsTheBlockAndKeepsTheRows() = runTest {
        val provider = providerWithCachedBlock()
        val before = cachedRows()
        channel.setState(denied)
        files.noAccess = true
        store.clearCache()
        system.log.clear()

        repeat(2) {
            assertIncludesBlock(provider)
            assertThat(cachedRows()).isEqualTo(before)
        }
        assertThat(system.log).isEmpty()
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenCachedIfwBlockAndMissingAccess_whenUnblockingTwice_thenReturnsNullAndKeepsTheRows() = runTest {
        val provider = providerWithCachedBlock()
        val before = cachedRows()
        channel.setState(denied)
        files.noAccess = true
        store.clearCache()
        system.log.clear()
        var controls = 0
        componentControl.apply = {
            controls++
            flowOf()
        }

        repeat(2) {
            assertThat(call(provider, "blocks", unblockInput())).isNull()
            assertThat(cachedRows()).isEqualTo(before)
        }
        assertThat(system.log).isEmpty()
        assertThat(controls).isEqualTo(0)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenAccessLostDuringRefresh_whenReading_thenReturnsTheCachedBlockWithoutStoringOtherChanges() = runTest {
        val provider = providerWithCachedBlock()
        val before = cachedRows()
        system.install(installed.copy(versionCode = 2))
        system.put(OWNER, tracker, PmSetting.DISABLED)
        files.noAccess = true

        assertIncludesBlock(provider)
        assertThat(cachedRows()).isEqualTo(before)
        assertThat(system.reads("pm ")).isEqualTo(2)
    }

    @Test
    fun givenUnreadableControlOutcome_whenUnblocking_thenKeepsKnownCacheValues() = runTest {
        val provider = providerWithCachedBlock()
        val before = cachedRows()
        componentControl.apply = {
            val outcome = ComponentOutcome(
                ComponentKey(tracker.id, OWNER),
                ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN),
                ControlResult.FAILED,
                mapOf(INTENT_FIREWALL to ControlFailure.ChannelUnavailable),
            )
            flowOf(ControlEvent.PackageDone(PKG, listOf(outcome)))
        }

        assertThat(call(provider, "blocks", unblockInput())).isNull()
        assertThat(cachedRows()).isEqualTo(before)
    }

    private suspend fun TestScope.providerWithCachedBlock(): ComponentProvider {
        val repository = components(backgroundScope)
        system.install(installed, tracker)
        store.setBlocked(PKG, mapOf(tracker to true))
        assertThat(repository.refresh(installed.key)).isEqualTo(ReadResult.Success(Unit))
        assertThat(repository.components(installed.key).first().single().state.ifw).isEqualTo(IfwBlock.BLOCKED)
        val app = ApplicationProvider.getApplicationContext<TestApp>()
        val scope = backgroundScope
        app.entryPoint = object : TestEntryPoint {
            override fun componentStatuses(): ComponentStatusRepository = repository
            override fun selectedUsers() = FakeSelectedUserRepository(OWNER)
            override fun controlLayers() = GetControlLayersUseCase(TestUserDataRepository())
            override fun privilegedChannel() = channel
            override fun analyticsHelper() = TestAnalyticsHelper()
            override fun applicationScope(): CoroutineScope = scope
            override fun ioDispatcher() = Dispatchers.IO
        }
        return Robolectric.buildContentProvider(ComponentProvider::class.java)
            .create("com.merxury.blocker.provider.test")
            .get()
    }

    private suspend fun cachedRows() = listOf(
        dao.getApp(PKG, OWNER.value),
        dao.getDeclarations(PKG),
        dao.scanMarker(PKG),
        dao.observeComponents(PKG, OWNER.value).first(),
    )

    private suspend fun assertIncludesBlock(provider: ComponentProvider) {
        val result = checkNotNull(call(provider, "getComponents"))
        assertThat(result.getString("cmp_list")).isEqualTo(
            """{"pkg":"$PKG","components":[{"type":"$PKG","name":"${tracker.id.className}","block":true}]}""",
        )
    }

    private fun unblockInput() = Bundle().apply {
        putString("cmp_list", """{"pkg":"$PKG","components":[{"type":"$PKG","name":"${tracker.id.className}","block":false}]}""")
    }

    private suspend fun call(provider: ComponentProvider, method: String, extras: Bundle? = null): Bundle? = withContext(Dispatchers.IO) {
        provider.call(method, PKG, extras)
    }
}
