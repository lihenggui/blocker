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
import android.os.Process
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.NoOpAnalyticsHelper
import com.merxury.blocker.core.data.history.OperationRecorder
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.provider.ComponentProvider
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContentProvider
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = ProviderOperationRecordingTest.TestApp::class)
class ProviderOperationRecordingTest : DeviceRepositoryTestBase() {
    @Test
    fun givenVerifiedCallerWithBlockAndEnableRequests_whenProviderWrites_thenOneOperationHasTheCallerReference() = runTest {
        assertProviderHistory(reportedPackage = CALLER, expectedRef = CALLER)
    }

    @Test
    fun givenCallerPackageOutsideBinderUid_whenProviderWrites_thenTheOperationReferenceIsNull() = runTest {
        assertProviderHistory(reportedPackage = PKG, expectedRef = null)
    }

    private suspend fun TestScope.assertProviderHistory(reportedPackage: String, expectedRef: String?) {
        val block = declaration("Block", ComponentType.SERVICE)
        val enable = declaration("Enable", ComponentType.SERVICE)
        system.install(installedApp(PKG), block, enable)
        store.setBlocked(PKG, mapOf(enable to true))
        val repository = components(backgroundScope, ShellRefusingComponentControl.create(channel, system, store, packageLocks))
        val history = historyDb.operationHistoryDao()
        val recorder = OperationRecorder({ history }, Clock.System)
        val userData = TestUserDataRepository().apply { setControllerType(ControllerType.IFW) }
        val selectedUsers = FakeSelectedUserRepository(OWNER)
        val app = applicationContext as TestApp
        val scope = backgroundScope
        app.entryPoint = object : TestEntryPoint {
            override fun componentStatuses() = repository
            override fun selectedUsers() = selectedUsers
            override fun controlLayers() = GetControlLayersUseCase(userData)
            override fun operationRecorder() = recorder
            override fun privilegedChannel() = channel
            override fun analyticsHelper() = NoOpAnalyticsHelper()
            override fun applicationScope() = scope
            override fun ioDispatcher() = Dispatchers.IO
        }
        shadowOf(app.packageManager).setPackagesForUid(Process.myUid(), CALLER)
        val provider = ContentProviderController.of(ComponentProvider()).create("com.merxury.blocker.provider.test").get()
        Shadow.extract<ShadowContentProvider>(provider).setCallingPackage(reportedPackage)
        val input = Bundle().apply {
            putString(
                "cmp_list",
                """
                {"pkg":"com.example.extras","components":[
                    {"type":"ignored","name":"${block.id.className}","block":true},
                    {"type":"ignored","name":"${enable.id.className}","block":false}
                ]}
                """.trimIndent(),
            )
        }

        val result = withContext(Dispatchers.IO) { provider.call("blocks", PKG, input) }

        assertThat(result).isSameInstanceAs(input)
        val operation = history.observeOperations().first().single()
        assertThat(operation.source).isEqualTo(OperationSource.EXTERNAL_PROVIDER)
        assertThat(operation.ref).isEqualTo(expectedRef)
        val entries = history.getEntries(operation.id)
        assertThat(entries.map { it.componentName }).containsExactly(block.id.className, enable.id.className)
    }

    interface TestEntryPoint :
        ComponentProvider.ProviderEntryPoint,
        GeneratedComponent

    class TestApp :
        Application(),
        GeneratedComponentManager<TestEntryPoint> {
        lateinit var entryPoint: TestEntryPoint

        override fun generatedComponent(): TestEntryPoint = entryPoint
    }

    private companion object {
        const val CALLER = "com.example.caller"
    }
}
