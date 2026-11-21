// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.core.ifw.WakeServiceFilter
import com.merxury.core.ifw.renderWakeRules
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WakeFirewallDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun givenManualAndManagedRules_whenRemovingManagedRule_thenOnlyItsActionBecomesAvailable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wakeFirewallTest") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val directory = "/data/system/ifw"
        val managed = "$directory/blocker-instrumentation-managed.xml"
        val manual = "$directory/blocker-instrumentation-manual.xml"
        assertTrue(shell("su 0 id").contains("uid=0"))
        shell("su 0 mkdir -p $directory")
        val existing = shell("su 0 ls $directory").lines()
        assertTrue(managed.substringAfterLast('/') !in existing && manual.substringAfterLast('/') !in existing)
        val keepalive = "test.peer.WAKE"
        val unrelated = "test.user.PAY"
        val independent = "test.manual.BLOCK"
        val filter = WakeServiceFilter(context.packageName, context.packageName, WakeFixtureService::class.java.name, keepalive)
        fun writeRule(path: String, action: String) {
            val xml = requireNotNull(renderWakeRules(listOf(filter.copy(action = action))))
            val (out, input) = instrumentation.uiAutomation.executeShellCommandRw("su 0 tee $path")
            ParcelFileDescriptor.AutoCloseOutputStream(input).bufferedWriter().use { it.write(xml) }
            assertTrue(ParcelFileDescriptor.AutoCloseInputStream(out).bufferedReader().use { it.readText() } == xml)
        }
        try {
            assertTrue(bind(keepalive))
            writeRule(manual, independent)
            writeRule(managed, keepalive)
            assertTrue(waitUntil { !bind(keepalive) && !bind(independent) })
            assertTrue(bind(unrelated))
            shell("su 0 rm $managed")
            assertTrue(waitUntil { bind(keepalive) })
            assertTrue(!bind(independent))
        } finally {
            shell("su 0 rm -f $managed $manual")
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

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
