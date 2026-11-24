// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.InMemoryRootGrantMemory
import com.merxury.blocker.core.privileged.connectedShizukuTransport
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeServiceFilter
import com.merxury.core.ifw.renderWakeRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Checks that the system enforces the managed wake rules the wake feature writes through the
 * privileged channel, next to a manual rule file the test writes through the same channel.
 * Argument `wakeFirewallTest=root` (su allows this package) or `shizuku-root` (Shizuku runs as root
 * and allows this package). Without it the test is skipped.
 */
class WakeFirewallDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before
    fun allowHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    @Test fun givenManualAndManagedRules_whenRemovingManagedRule_thenOnlyItsActionBecomesAvailable() {
        val case = InstrumentationRegistry.getArguments().getString("wakeFirewallTest")
        assumeTrue(case == "root" || case == "shizuku-root")
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val transport = if (case == "root") {
            RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also {
                assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), runBlocking { it.connect() })
            }
        } else {
            connectedShizukuTransport(context, PrivilegedIdentity.ROOT)
        }
        val writer = AuthorizedWakeRuleWriter(transport, context)
        val manual = "/data/system/ifw/blocker-instrumentation-manual.xml"
        assertEquals(CallResult.Success(null), runBlocking { transport.execute(StatFile(manual)) })
        val keepalive = "test.peer.WAKE"
        val unrelated = "test.user.PAY"
        val independent = "test.manual.BLOCK"
        val filter = WakeServiceFilter(context.packageName, context.packageName, WakeFixtureService::class.java.name, keepalive)
        try {
            assertTrue(bind(keepalive))
            val xml = requireNotNull(renderWakeRules(listOf(filter.copy(action = independent))))
            assertEquals(CallResult.Success(Unit), runBlocking { transport.execute(WriteFile(manual, xml)) })
            runBlocking { writer.write(WakeRuleFileKind.MANAGED, renderWakeRules(listOf(filter))) }
            assertTrue(waitUntil { !bind(keepalive) && !bind(independent) })
            assertTrue(bind(unrelated))
            runBlocking { writer.reset(context.packageName) }
            assertTrue(waitUntil { bind(keepalive) })
            assertTrue(!bind(independent))
        } finally {
            try {
                runBlocking { writer.reset(null) }
            } finally {
                runBlocking { transport.execute(DeleteFiles(listOf(manual))) }
            }
        }
    }

    private fun bind(action: String): Boolean {
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = connected.countDown()
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val success = try {
            context.bindService(Intent(context, WakeFixtureService::class.java).setAction(action), connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            if (e.message?.startsWith("Not allowed to bind to service") == true && e.stackTrace.firstOrNull()?.className == "android.app.ContextImpl") return false
            throw e
        }
        if (!success) return false
        return try {
            assertTrue("Allowed fixture service did not connect", connected.await(30, TimeUnit.SECONDS))
            true
        } finally {
            context.unbindService(connection)
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(300)
        }
        return false
    }
}
