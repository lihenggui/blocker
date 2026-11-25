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

package com.merxury.blocker.core.privileged

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.calls.ListIfwRuleFiles
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.privileged.wake.WakeFixtureService
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.di.IfwModule
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TAG = "IfwStoreDeviceTest"

/**
 * The Intent Firewall store against the real rule directory, writing the rule file of this test
 * package and checking what Android makes of it.
 *
 * Arguments: `ifwStoreTest=root` goes through su granted to this test package;
 * `ifwStoreTest=shizuku` through the user service of a Shizuku server running as root;
 * `ifwStoreTest=system` through the user service of a Shizuku server running as the system uid;
 * `ifwStoreTest=shell` through a Shizuku server running as the shell, which must get no access.
 * Shizuku must have been started after this package was granted, see [connectedShizukuTransport].
 */
class IfwStoreDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val self = context.packageName
    private val mode = InstrumentationRegistry.getArguments().getString("ifwStoreTest")
    private val activity = ComponentDeclaration(ComponentId(self, IfwFixtureActivity::class.java.name), ComponentType.ACTIVITY, exported = true, enabledInManifest = true)
    private val service = ComponentDeclaration(ComponentId(self, WakeFixtureService::class.java.name), ComponentType.SERVICE, exported = false, enabledInManifest = true)

    private lateinit var transport: AbstractPrivilegedTransport
    private lateinit var store: IfwStore

    @Before
    fun connect() {
        assumeTrue(mode in setOf("root", "shizuku", "system", "shell"))
        // The Shizuku transport calls hidden interfaces in this process, as the app does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
        transport = when (mode) {
            "root" -> RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also {
                assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), runBlocking { it.connect() })
            }

            "shell" -> connectedShizukuTransport(context, PrivilegedIdentity.SHELL)

            "system" -> connectedShizukuTransport(context, PrivilegedIdentity.SYSTEM)

            else -> connectedShizukuTransport(context, PrivilegedIdentity.ROOT)
        }
        store = IfwModule.providesIfwStore(ChannelIfwFileSystem(transport), IfwXmlSerializer(), IfwXmlDeserializer(), "com.merxury.blocker.owner")
    }

    @After
    fun removeRuleFile() = runBlocking<Unit> {
        // Without the instrumentation argument the setup is skipped and there is nothing to remove.
        if (mode == "shell" || !::store.isInitialized) return@runBlocking
        store.update(self) { IfwRules.empty() }
    }

    @Test
    fun givenRootChannel_whenBlockAndUnblockActivity_thenStartFailsAndWorksAgain() = runBlocking<Unit> {
        assumeTrue(mode != "shell")
        assertStarts()

        assertEquals(IfwResult.Success(mapOf(activity.id to IfwBlock.BLOCKED)), store.setBlocked(self, mapOf(activity to true)))

        val stat = transport.execute(StatIfwRuleFile(self)).value()
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} via $mode: $stat")
        assertTrue(stat.startsWith("file 644 "), stat)
        val written = IfwXmlDeserializer().deserialize(checkNotNull(ChannelIfwFileSystem(transport).readRules(self)))
        assertEquals(IfwRules(listOf(IfwRule(IfwComponentType.ACTIVITY, filters = listOf(IfwFilter.ComponentFilter(activity.id.flatten()))))), written)
        assertStartBlocked()

        assertEquals(IfwResult.Success(mapOf(activity.id to IfwBlock.NOT_BLOCKED)), store.setBlocked(self, mapOf(activity to false)))
        assertEquals(IfwResult.Success(emptyMap<String, IfwResult<IfwRules>>()), store.allRules().onlySelf())
        assertStarts()
    }

    @Test
    fun givenHandWrittenConditionalRule_whenBlockAndUnblockAnother_thenRuleSurvivesAndStaysConditional() = runBlocking<Unit> {
        assumeTrue(mode != "shell")
        // Applies only to starts from a package that does not exist, so the shell's start passes.
        val conditional = IfwRule(
            IfwComponentType.ACTIVITY,
            filters = listOf(IfwFilter.ComponentFilter(activity.id.flatten()), IfwFilter.SenderPackage("com.example.nobody")),
        )
        store.update(self) { IfwRules(listOf(conditional)) }

        store.setBlocked(self, mapOf(service to true))
        store.setBlocked(self, mapOf(service to false))

        assertEquals(IfwResult.Success(IfwRules(listOf(conditional))), store.rules(self))
        assertEquals(mapOf(activity.id to IfwBlock.CONDITIONAL, service.id to IfwBlock.NOT_BLOCKED), store.blocks(self, listOf(activity, service)))
        assertStarts()
    }

    @Test
    fun givenRulesLargerThanOneReply_whenReadBack_thenWhole() = runBlocking<Unit> {
        assumeTrue(mode != "shell")
        // Far more than one binder transaction carries, so the request and the answer both take
        // the pipe.
        val many = (1..1500).associate {
            ComponentDeclaration(ComponentId(self, "$self.generated.Receiver$it"), ComponentType.RECEIVER, exported = true, enabledInManifest = true) to true
        }

        val result = store.setBlocked(self, many)

        assertTrue(result is IfwResult.Success && result.value.values.all { it == IfwBlock.BLOCKED }, "$result")
        val content = checkNotNull(ChannelIfwFileSystem(transport).readRules(self))
        Log.i(TAG, "${many.size} filters took ${content.length} chars")
        assertTrue(content.length * 2 > 300 * 1024, "Too small: ${content.length}")
        store.clearCache()
        assertTrue(store.blocks(self, many.keys).values.all { it == IfwBlock.BLOCKED })
    }

    @Test
    fun givenShellChannel_whenEachOperation_thenNoAccess() = runBlocking<Unit> {
        assumeTrue(mode == "shell")

        assertEquals(IfwResult.Failure(IfwFailure.NoAccess), store.setBlocked(self, mapOf(activity to true)))
        assertEquals(IfwResult.Failure(IfwFailure.NoAccess), store.rules(self))
        assertEquals(IfwResult.Failure(IfwFailure.NoAccess), store.allRules())
        assertEquals(mapOf(activity.id to IfwBlock.UNKNOWN), store.blocks(self, listOf(activity)))
        assertEquals(ChannelState.Ready(PrivilegedIdentity.SHELL), transport.state.value)
    }

    @Test
    fun givenUserServiceDiesDuringCall_whenNextCall_thenStartedAgainAndChannelStaysReady() = runBlocking<Unit> {
        assumeTrue(mode == "shizuku" || mode == "system")
        transport.execute(ListIfwRuleFiles).value()
        val ready = transport.state.value

        assertEquals(CallResult.Failure(CallFailure.ChannelUnavailable), transport.execute(EndPrivilegedProcess))
        assertEquals(ready, transport.state.value)
        transport.execute(ListIfwRuleFiles).value()
    }

    private fun IfwResult<Map<String, IfwResult<IfwRules>>>.onlySelf() = when (this) {
        is IfwResult.Success -> IfwResult.Success(value.filterKeys { it == self })
        is IfwResult.Failure -> this
    }

    private fun assertStarts() {
        val before = IfwFixtureActivity.starts.get()
        startFixture()
        assertTrue(waitFor(10_000) { IfwFixtureActivity.starts.get() > before }, "The fixture did not start")
    }

    private fun assertStartBlocked() {
        val before = IfwFixtureActivity.starts.get()
        startFixture()
        assertTrue(!waitFor(4_000) { IfwFixtureActivity.starts.get() > before }, "The fixture started although blocked")
    }

    private fun startFixture() {
        // system_server reloads the rules shortly after the directory changes.
        SystemClock.sleep(2_000)
        val output = shell("am start -n $self/${IfwFixtureActivity::class.java.name}")
        Log.i(TAG, "am start: ${output.trim()}")
    }

    private fun waitFor(millis: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private fun <R> CallResult<R>.value(): R = when (this) {
        is CallResult.Success -> value
        is CallResult.Failure -> throw AssertionError("Call failed: $reason")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader()
        .use { it.readText() }
}
