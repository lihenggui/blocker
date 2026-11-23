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

package com.merxury.blocker.core.control

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.controllers.wake.WakeFixtureService
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlLayer.PACKAGE_MANAGER
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.getOrNull
import com.merxury.blocker.core.privileged.AbstractPrivilegedTransport
import com.merxury.blocker.core.privileged.ChannelIfwFileSystem
import com.merxury.blocker.core.privileged.InMemoryRootGrantMemory
import com.merxury.blocker.core.privileged.PrivilegedPlatformPackages
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
import com.merxury.blocker.core.privileged.connectedShizukuTransport
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.root.BlockerRootSession
import com.merxury.blocker.core.system.LocalPlatformPackages
import com.merxury.blocker.core.system.PlatformSystemPackageReader
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.di.IfwModule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TAG = "ComponentControlTest"
private const val UIDS_PER_USER = 100_000

/**
 * [ComponentControl] against the real system, controlling this test package in another user.
 * Changing a component's setting stops its app in that user, which would end this run if it were
 * the user the run is in, so the target is always another user; the firewall rules it writes
 * apply to every user.
 *
 * Arguments: `componentControlTest=root` goes through su granted to this test package;
 * `componentControlTest=shell` through a Shizuku server running as the shell, started after this
 * package was granted, see [connectedShizukuTransport]. `targetUser=<id>` names the user whose
 * copy of this package is controlled, which must not be the user the run is in: a work profile
 * from a run in the owner, after `pm install-existing --user <id> <this package>`, or the owner
 * from a run in the profile started with `am instrument --user <id>`, for which su must accept
 * requests from that profile.
 */
class ComponentControlDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val self = context.packageName
    private val mode = arguments.getString("componentControlTest")
    private val ownUser = UserId(Process.myUid() / UIDS_PER_USER)

    private val target: UserId
        get() = UserId(requireNotNull(arguments.getString("targetUser")) { "Pass -e targetUser <id>" }.toInt())
    private lateinit var transport: AbstractPrivilegedTransport
    private lateinit var reader: PlatformSystemPackageReader
    private lateinit var store: IfwStore
    private lateinit var control: ComponentControl

    private lateinit var activity: ComponentDeclaration
    private lateinit var disabledActivity: ComponentDeclaration
    private lateinit var service: ComponentDeclaration
    private lateinit var provider: ComponentDeclaration
    private val fixtures get() = listOf(activity, disabledActivity, service, provider)

    @get:Rule
    val testName = TestName()

    @Before
    fun connect() = runBlocking<Unit> {
        // Each test names the identity it needs, so a run skips the others before connecting.
        assumeTrue(mode == "root" || mode == "shell")
        assumeTrue(testName.methodName.startsWith(if (mode == "root") "givenRoot_" else "givenShell_"))
        assertNotEquals(ownUser, target, "The target user must not be the user this run is in")
        // The Shizuku transport calls hidden interfaces in this process, as the app does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
        transport = if (mode == "root") {
            RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also { assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), it.connect()) }
        } else {
            connectedShizukuTransport(context, PrivilegedIdentity.SHELL)
        }
        reader = PlatformSystemPackageReader(PrivilegedPlatformPackages(transport, LocalPlatformPackages(context)), Dispatchers.IO)
        store = IfwModule.providesIfwStore(ChannelIfwFileSystem(transport), IfwXmlSerializer(), IfwXmlDeserializer())
        control = PrivilegedComponentControl(transport, transport, reader, store, PackageLocks())
        val declared = checkNotNull(reader.declarations(AppKey(self, target)).getOrNull()) { "$self is not installed for user ${target.value}" }
        fun declaration(className: String) = declared.single { it.id.className == className }
        activity = declaration(ControlFixtureActivity::class.java.name)
        disabledActivity = declaration(ControlFixtureDisabledActivity::class.java.name)
        service = declaration(WakeFixtureService::class.java.name)
        provider = declaration(ControlFixtureProvider::class.java.name)
        assertFalse(disabledActivity.enabledInManifest)
        assertEquals(ComponentType.PROVIDER, provider.type)
        Log.i(TAG, "API ${Build.VERSION.SDK_INT}, run in user ${ownUser.value}, target user ${target.value}, via $mode")
    }

    @After
    fun restore() = runBlocking<Unit> {
        if (mode != "root" || !::provider.isInitialized) return@runBlocking
        val names = fixtures.map { ComponentName(self, it.id.className) }
        transport.execute(SetComponentEnabledSettings(names, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, target))
        store.update(self) { IfwRules.empty() }
    }

    @Test
    fun givenRoot_whenBlockWithPmThenUnblock_thenTargetUserOnlyAndNoOverrideLeft() = runBlocking<Unit> {
        assertStarts(true)

        val blocked = apply(ControlGoal.BLOCK, PM, activity, service, provider)

        blocked.forEach { assertAchieved(it, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)) }
        assertEquals(listOf(PmSetting.DISABLED, PmSetting.DISABLED, PmSetting.DISABLED), pmSettings(activity, service, provider))
        assertEquals(mapOf(activity to "disabled", service to "disabled", provider to "disabled"), overrides(target))
        assertEquals(PmSetting.DEFAULT, reader.pmSettings(AppKey(self, ownUser), listOf(activity.id)).getOrNull()?.get(activity.id))
        assertNull(ruleFile())
        assertStarts(false)

        val unblocked = apply(ControlGoal.UNBLOCK, PM, activity, service, provider)

        unblocked.forEach { assertAchieved(it, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)) }
        assertEquals(emptyMap<ComponentDeclaration, String>(), overrides(target))
        assertStarts(true)
    }

    @Test
    fun givenRoot_whenBlockWithIfwThenUnblock_thenRuleForFilteredTypesAndPmForProvider() = runBlocking<Unit> {
        val blocked = apply(ControlGoal.BLOCK, IFW, activity, service, provider)

        assertAchieved(blocked[0], ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertAchieved(blocked[1], ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertAchieved(blocked[2], ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        val rules = checkNotNull(ruleFile())
        assertTrue(activity.id.flatten() in rules && service.id.flatten() in rules, rules)
        assertFalse(provider.id.className in rules, rules)
        assertEquals(mapOf(provider to "disabled"), overrides(target))
        assertStarts(false)

        val unblocked = apply(ControlGoal.UNBLOCK, IFW, activity, service, provider)

        unblocked.forEach { assertAchieved(it, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)) }
        assertNull(ruleFile())
        assertEquals(emptyMap<ComponentDeclaration, String>(), overrides(target))
        assertStarts(true)
    }

    @Test
    fun givenRoot_whenBlockWithBothThenUnblock_thenBothLayersSetAndCleared() = runBlocking<Unit> {
        val blocked = apply(ControlGoal.BLOCK, BOTH, activity, service)

        blocked.forEach { assertAchieved(it, ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED)) }
        assertEquals(mapOf(activity to "disabled", service to "disabled"), overrides(target))
        assertTrue(activity.id.flatten() in checkNotNull(ruleFile()))
        assertStarts(false)

        val unblocked = apply(ControlGoal.UNBLOCK, BOTH, activity, service)

        unblocked.forEach { assertAchieved(it, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)) }
        assertEquals(emptyMap<ComponentDeclaration, String>(), overrides(target))
        assertNull(ruleFile())
        assertStarts(true)
    }

    @Test
    fun givenRoot_whenUnblockManifestDisabledComponent_thenLeftAtDefaultAndStillNotStarting() = runBlocking<Unit> {
        assertStarts(false, disabledActivity)

        val unblocked = apply(ControlGoal.UNBLOCK, PM, disabledActivity)

        assertAchieved(unblocked.single(), ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertEquals(emptyMap<ComponentDeclaration, String>(), overrides(target))
        assertStarts(false, disabledActivity)
    }

    @Test
    fun givenRoot_whenEnableManifestDisabledComponent_thenEnabledAndStarts() = runBlocking<Unit> {
        assertStarts(false, disabledActivity)

        val enabled = apply(ControlGoal.ENABLE, PM, disabledActivity)

        assertAchieved(enabled.single(), ControlState(PmSetting.ENABLED, IfwBlock.NOT_BLOCKED))
        assertEquals(mapOf(disabledActivity to "enabled"), overrides(target))
        assertStarts(true, disabledActivity)
    }

    @Test
    fun givenRoot_whenBatchHoldsUndeclaredComponent_thenItFailsAloneAndOthersBlocked() = runBlocking<Unit> {
        // Root is refused per component only for a class the app does not declare; protected
        // packages are refused as a whole. A stale declaration is how this reaches a batch.
        val ghost = ComponentDeclaration(ComponentId(self, "$self.NotDeclared"), ComponentType.ACTIVITY, exported = true, enabledInManifest = true)

        val outcomes = apply(ControlGoal.BLOCK, PM, activity, ghost, service)

        assertAchieved(outcomes[0], ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        assertAchieved(outcomes[2], ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        val refused = outcomes[1]
        Log.i(TAG, "Undeclared component in a batch: $refused")
        assertEquals(ControlResult.FAILED, refused.result)
        assertIs<ControlFailure.TargetNotFound>(refused.failures[PACKAGE_MANAGER])
    }

    @Test
    fun givenShell_whenBlockWithPm_thenNotPermittedAndNothingChanged() = runBlocking<Unit> {
        val outcome = apply(ControlGoal.BLOCK, PM, activity).single()

        Log.i(TAG, "Shell block with PM: $outcome")
        assertEquals(ControlResult.FAILED, outcome.result)
        assertIs<ControlFailure.NotPermitted>(outcome.failures[PACKAGE_MANAGER])
        assertEquals(PmSetting.DEFAULT, outcome.state.pm)
        assertEquals(emptyMap<ComponentDeclaration, String>(), overrides(target))
        assertStarts(true)
    }

    @Test
    fun givenShell_whenBlockWithIfw_thenNotPermittedAndStateUnknown() = runBlocking<Unit> {
        val outcome = apply(ControlGoal.BLOCK, IFW, activity).single()

        Log.i(TAG, "Shell block with IFW: $outcome")
        assertEquals(ControlResult.FAILED, outcome.result)
        assertIs<ControlFailure.NotPermitted>(outcome.failures[INTENT_FIREWALL])
        assertEquals(ControlState(PmSetting.DEFAULT, IfwBlock.UNKNOWN), outcome.state)
        assertStarts(true)
    }

    private suspend fun apply(goal: ControlGoal, layers: Set<ControlLayer>, vararg components: ComponentDeclaration): List<ComponentOutcome> {
        val events = control.apply(ControlRequest(target, components.toList(), goal, layers)).toList()
        Log.i(TAG, "$goal $layers: $events")
        val done = events.filterIsInstance<ControlEvent.PackageDone>().single()
        assertIs<ControlEvent.Finished>(events.last())
        return done.outcomes.also { outcomes -> assertEquals(components.map { it.id }, outcomes.map { it.key.id }) }
    }

    private fun assertAchieved(outcome: ComponentOutcome, state: ControlState) {
        assertEquals(ControlResult.ACHIEVED, outcome.result, "$outcome")
        assertEquals(state, outcome.state, "${outcome.key}")
        assertEquals(target, outcome.key.userId)
    }

    private suspend fun pmSettings(vararg components: ComponentDeclaration): List<PmSetting?> {
        val settings = reader.pmSettings(AppKey(self, target), components.map { it.id }).getOrNull()
        return components.map { settings?.get(it.id) }
    }

    /**
     * The overrides `dumpsys package` lists for [user], each as `disabled` or `enabled`. A
     * component at DEFAULT has none: the per-user settings hold no entry for it.
     */
    private fun overrides(user: UserId): Map<ComponentDeclaration, String> {
        val lines = shell("dumpsys package $self").lines()
        val start = lines.indexOfFirst { it.trim().startsWith("User ${user.value}: ceDataInode") }
        assertTrue(start >= 0, "No state of user ${user.value} for $self")
        val block = lines.drop(start + 1).takeWhile { it.isNotBlank() && !it.trim().matches(Regex("User \\d+: ceDataInode.*")) }
        val found = mutableMapOf<ComponentDeclaration, String>()
        var kind: String? = null
        block.map { it.trim() }.forEach { line ->
            when {
                line == "disabledComponents:" -> kind = "disabled"
                line == "enabledComponents:" -> kind = "enabled"
                line.endsWith(":") -> kind = null
                kind != null -> fixtures.find { it.id.className == line }?.let { found[it] = kind!! }
            }
        }
        return found
    }

    private suspend fun ruleFile(): String? = ChannelIfwFileSystem(transport).readRules(self)

    /** Starts [component] in the target user and checks whether it ran. */
    private fun assertStarts(expected: Boolean, component: ComponentDeclaration = activity) {
        // system_server reloads the firewall rules shortly after the directory changes.
        SystemClock.sleep(2_000)
        val nonce = UUID.randomUUID().toString()
        val output = shell("am start --user ${target.value} -n ${component.id.flatten()} --es $NONCE_EXTRA $nonce")
        Log.i(TAG, "am start ${component.id.simpleName}: ${output.trim()}")
        val started = waitFor(if (expected) 10_000 else 4_000) { "started $nonce" in shell("logcat -d -s $FIXTURE_TAG:I") }
        assertEquals(expected, started, "${component.id.simpleName} started in user ${target.value}: $started")
    }

    private fun waitFor(millis: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(250)
        }
        return condition()
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader()
        .use { it.readText() }

    private companion object {
        val PM = setOf(PACKAGE_MANAGER)
        val IFW = setOf(INTENT_FIREWALL)
        val BOTH = setOf(PACKAGE_MANAGER, INTENT_FIREWALL)
    }
}
