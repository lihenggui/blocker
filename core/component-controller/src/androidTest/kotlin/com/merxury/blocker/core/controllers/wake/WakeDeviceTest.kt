// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.controllers.root.api.ManagedComponentState
import com.merxury.blocker.core.root.BlockerRootSession
import com.merxury.blocker.core.root.RootCommandExecutor
import com.merxury.blocker.core.utils.LibrootRootAvailabilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WakeFixtureService : Service() {
    override fun onBind(intent: Intent): IBinder = Binder()
}

class WakeDeviceTest {
    @Test fun authorizedBinderFallbackRetainsPackageAndServiceDiagnostics() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wakeDeviceTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.IO) {
            repeat(30) { if (!Shizuku.pingBinder()) delay(200) }
            assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
            val boot = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
            val session = WakeSession(Shizuku.getUid(), null, boot, binderOnly = true)
            assertTrue(0 in WakeSystemEvidence.users(session.read(WakeCommand.USERS).decodeToString()))
            assertTrue(!session.supportsRecorder)
            val packages = WakeSystemEvidence.packages(session.read(WakeCommand.PACKAGES).decodeToString())
            assertTrue(packages.any { it.packageName == context.packageName })
            assertTrue(session.read(WakeCommand.SERVICES).decodeToString().contains("ACTIVITY MANAGER SERVICES"))
        }
    }

    @Test fun rootShizukuChangesReviewedComponentWithoutKillingItsProcess() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wakeRootServiceTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.IO) {
            repeat(30) { if (!Shizuku.pingBinder()) delay(200) }
            val root = BlockerRootSession(context)
            val reader = WakeSystemReader(context, LibrootRootAvailabilityChecker(root, context))
            assertEquals(WakeAccess.SHIZUKU_ROOT, reader.access())
            val controller = ManagedComponentState(RootCommandExecutor(root), reader, context)
            val fixture = ComponentName(context, WakeFixtureService::class.java)
            val original = context.packageManager.getComponentEnabledSetting(fixture)
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, original)
            val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                assertTrue(controller.compareAndSet(fixture.packageName, fixture.className, original, disabled))
                assertEquals(disabled, context.packageManager.getComponentEnabledSetting(fixture))
            } finally {
                assertTrue(controller.compareAndSet(fixture.packageName, fixture.className, disabled, original))
            }
        }
    }

    @Test fun rootUserServiceAppliesAndRemovesTheManagedProbe() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wakeRootServiceTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.IO) {
            repeat(30) { if (!Shizuku.pingBinder()) delay(200) }
            val reader = WakeSystemReader(context, LibrootRootAvailabilityChecker(BlockerRootSession(context), context))
            assertEquals(WakeAccess.SHIZUKU_ROOT, reader.access())
            assertTrue("blocker-wake-capability-probe.xml" !in shell("su 0 ls /data/system/ifw").lines())
            reader.withSession(WakeAccess.SHIZUKU_ROOT) {
                try {
                    it.writeFirewall("<rules/>", true)
                    assertEquals("<rules/>", shell("su 0 cat /data/system/ifw/blocker-wake-capability-probe.xml"))
                } finally {
                    it.writeFirewall(null, true)
                }
                assertTrue("blocker-wake-capability-probe.xml" !in shell("su 0 ls /data/system/ifw").lines())
            }
        }
    }

    private fun shell(command: String): String {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val result = ui.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(result).bufferedReader().use { it.readText().trim() }
    }

    @Test fun authorizedShellSeesServiceRequestAndCleansItsOwnConfig() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wakeDeviceTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.IO) {
            repeat(30) {
                if (!Shizuku.pingBinder()) delay(200)
            }
            assertTrue("Start Shizuku and authorize the instrumentation package", Shizuku.pingBinder())
            assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
            val reader = WakeSystemReader(context, LibrootRootAvailabilityChecker(BlockerRootSession(context), context))
            val access = reader.access()
            reader.withSession(access) { assertTrue(it.supportsRecorder) }
            reader.withSession(access) { session ->
                val id = WakeStatsCodec.configId(Process.myUid(), session.generation)
                session.read(WakeCommand.INSTALL)
                val latch = CountDownLatch(1)
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        latch.countDown()
                    }
                    override fun onServiceDisconnected(name: ComponentName) = Unit
                }
                var bound = false
                try {
                    val action = "${context.packageName}.WAKE_FIXTURE"
                    bound = context.bindService(Intent(context, WakeFixtureService::class.java).setAction(action), connection, Context.BIND_AUTO_CREATE)
                    assertTrue(bound)
                    assertTrue(latch.await(30, TimeUnit.SECONDS))
                    var found = false
                    for (attempt in 1..12) {
                        delay(750)
                        val data = session.read(WakeCommand.REPORT)
                        val elapsed = SystemClock.elapsedRealtimeNanos()
                        val batch = WakeStatsCodec.decode(data, System.currentTimeMillis() * 1_000_000 - elapsed, elapsed, session.uid, id)
                        found = batch.observations.any {
                            it.kind == "ServiceRequestEventReported" && it.targetUid == Process.myUid() &&
                                it.callerUid == Process.myUid() && it.component?.endsWith("WakeFixtureService") == true
                        }
                        if (found) break
                    }
                    assertTrue("Framework omitted the test service request", found)
                } finally {
                    if (bound) context.unbindService(connection)
                    session.read(WakeCommand.REMOVE)
                }
            }
        }
    }
}
