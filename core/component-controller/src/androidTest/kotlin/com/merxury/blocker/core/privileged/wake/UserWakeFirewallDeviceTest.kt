// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.InMemoryRootGrantMemory
import com.merxury.blocker.core.privileged.connectedShizukuTransport
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.core.ifw.UserWakeRule
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.renderUserWakeRules
import com.merxury.core.ifw.wakeRuleFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

private const val WAKE_ACTION = "com.merxury.blocker.test.USER_WAKE"
private const val OTHER_ACTION = "com.merxury.blocker.test.OTHER_WAKE"

class UserWakeFixtureService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

class UserWakeFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        resultCode = Activity.RESULT_OK
    }
}

/** Runs with the root or root-backed Shizuku setup of [WakeFirewallDeviceTest]. */
class UserWakeFirewallDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun givenUserServiceRule_whenTwoCallersStartService_thenOnlyTheSelectedSenderIsBlocked() = runTest {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        withUserRules { writer ->
            val service = Intent(context, UserWakeFixtureService::class.java)
            val shellStart = "am startservice --user 0 -n ${context.packageName}/${UserWakeFixtureService::class.java.name}"
            assertEquals("2000", shell("id -u").trim())
            assertEquals("The selected sender can start the service before the rule", "", serviceStartError(shellStart))
            assertNotNull(context.startService(service))

            val rule = UserWakeRule(UserWakeRule.Type.SERVICE, "com.android.shell", context.packageName, UserWakeFixtureService::class.java.name, null)
            writer.write(WakeRuleFileKind.USER, renderUserWakeRules(listOf(rule)))

            waitUntil("The selected sender can still start the service") { "blocked by firewall" in serviceStartError(shellStart) }
            assertNotNull("The test app must still be allowed", context.startService(service))

            writer.write(WakeRuleFileKind.USER, null)
            waitUntil("The selected sender did not recover") { serviceStartError(shellStart).isEmpty() }
        }
    }

    @Test fun givenUserActionRule_whenBroadcastNamesReceiver_thenOnlyTheMatchingActionIsBlocked() = runTest {
        withUserRules { writer ->
            val matching = Intent(context, UserWakeFixtureReceiver::class.java).setAction(WAKE_ACTION)
            val unmatched = Intent(context, UserWakeFixtureReceiver::class.java).setAction(OTHER_ACTION)
            val packageOnly = Intent(WAKE_ACTION).setPackage(context.packageName)
            assertTrue(delivered(matching))
            assertTrue(delivered(unmatched))
            assertTrue(delivered(packageOnly))

            val rule = UserWakeRule(UserWakeRule.Type.BROADCAST, null, context.packageName, null, WAKE_ACTION)
            writer.write(WakeRuleFileKind.USER, renderUserWakeRules(listOf(rule)))

            waitUntil("The action rule did not stop the explicit broadcast") { !delivered(matching) }
            assertTrue(delivered(unmatched))
            // AOSP derives component-package from Intent.getComponent(), which is null here.
            assertTrue(delivered(packageOnly))

            writer.write(WakeRuleFileKind.USER, null)
            waitUntil("The matching action did not recover") { delivered(matching) }
        }
    }

    private suspend fun withUserRules(action: suspend (AuthorizedWakeRuleWriter) -> Unit) {
        val mode = InstrumentationRegistry.getArguments().getString("wakeFirewallTest")
        assumeTrue(mode == "root" || mode == "shizuku-root")
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        HiddenApiBypass.addHiddenApiExemptions("")
        val transport = if (mode == "root") {
            RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also {
                assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), it.connect())
            }
        } else {
            connectedShizukuTransport(context, PrivilegedIdentity.ROOT)
        }
        val path = "/data/system/ifw/${wakeRuleFileName(context.packageName, WakeRuleFileKind.USER)}.xml"
        val writer = AuthorizedWakeRuleWriter(transport, context)
        assertEquals(CallResult.Success(null), transport.execute(StatFile(path)))
        try {
            action(writer)
        } finally {
            withContext(NonCancellable) { writer.write(WakeRuleFileKind.USER, null) }
        }
    }

    private fun delivered(intent: Intent): Boolean {
        val result = ArrayBlockingQueue<Int>(1)
        val acknowledgement = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result.add(resultCode)
            }
        }
        context.sendOrderedBroadcast(intent, null, acknowledgement, null, Activity.RESULT_CANCELED, null, null)
        val code = result.poll(5, TimeUnit.SECONDS)
        assertNotNull("The ordered broadcast did not finish", code)
        return code == Activity.RESULT_OK
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }

    @RequiresApi(34)
    private fun serviceStartError(command: String): String {
        val (stdout, stdin, stderr) = instrumentation.uiAutomation.executeShellCommandRwe(command)
        stdin.close()
        ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes() }
        return ParcelFileDescriptor.AutoCloseInputStream(stderr).bufferedReader().use { it.readText().trim() }
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        do {
            if (condition()) return
            SystemClock.sleep(250)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(message)
    }
}
