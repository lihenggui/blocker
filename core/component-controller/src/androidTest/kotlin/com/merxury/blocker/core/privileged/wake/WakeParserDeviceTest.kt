// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.model.wake.normalizeWakeTag
import com.merxury.blocker.core.model.wake.wakePathKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Runs every wake parser on Android's regex engine. java.util.regex on Android is backed by ICU,
 * which rejects some patterns the JVM accepts, such as an unescaped closing brace, so JVM unit
 * tests alone cannot catch them. Each input reaches every pattern of its parser.
 */
class WakeParserDeviceTest {
    private fun snapshot(command: WakeCommand, text: String) = WakeSystemEvidence.snapshot(command, text.trimIndent(), 1)

    @Test fun givenEachSnapshotShape_whenParsedOnAndroid_thenEveryPatternCompilesAndMatches() {
        val services = """
            * ServiceRecord{abc u0 remote.pkg/.Service}
              app=ProcessRecord{abc 33:remote.pkg/u0a22}
              * IntentBindRecord{aaa CREATE}:
                intent={act=sdk.peer.WAKE cmp=remote.pkg/.Service}
                * Client AppBindRecord{aaa ProcessRecord{abc 44:first.pkg/u0a11}}
        """
        assertEquals("sdk.peer.WAKE", snapshot(WakeCommand.SERVICES, services).single().action)
        val providers = "* ContentProviderRecord{abcdef u0 example.app/.Provider}\n  Connections:\n    -> 23:example.client/u0a170 s1/1 u0/0"
        assertEquals("example.client", snapshot(WakeCommand.PROVIDERS, providers).single().attributes["client_process"])
        val oom = """
            Proc #19: psvc   F/ /PER  LCMNFUA  t: 0 1414:com.google.android.providers.media.module/u0a203 (service)
                oom: max=1001 curRaw=0 setRaw=0 cur=0 set=-700
        """
        assertEquals("-700", snapshot(WakeCommand.PROCESSES, oom).single().attributes["oom_adj"])
        assertEquals(1, snapshot(WakeCommand.WIDGETS, "provider=ComponentInfo{example.app/example.app.Widget}").size)
        assertEquals("example.home", snapshot(WakeCommand.ROLES, "name=android.app.role.HOME\nholders=[example.home]").single().targetPackage)
        assertEquals("example.ime/.Ime", snapshot(WakeCommand.INPUT, "example.ime/.Ime").single().component)
        assertEquals("example.sync/.JobService", snapshot(WakeCommand.JOBS, "JOB #u0a12/17: example.sync/.JobService").single().component)
        val intents = "#0: PendingIntentRecord{123 example.client broadcastIntent}\n uid=10123 packageName=example.client\n requestIntent=act=example.UPDATE cmp=example.app/.Receiver"
        assertEquals("example.UPDATE", snapshot(WakeCommand.INTENTS, intents).single().action)
        val alarms = """
            ELAPSED_WAKEUP #3: Alarm{58c30ff type 2 origWhen 8097603 whenElapsed 8097603 com.google.android.gms}
              operation=PendingIntent{106dcd: PendingIntentRecord{eba962c com.google.android.gms/com.google.android.gms.gcm broadcastIntent}}
        """
        assertEquals("com.google.android.gms", snapshot(WakeCommand.ALARMS, alarms).single().callerPackage)
        assertEquals("example.watch", snapshot(WakeCommand.COMPANIONS, "Association{id=1, packageName=example.watch}").single().targetPackage)
        assertEquals(1, snapshot(WakeCommand.WALLPAPER, "mWallpaperComponent=ComponentInfo{example.wall/example.wall.Service}").size)
        assertEquals("example.sync", snapshot(WakeCommand.SYNCS, "ServiceInfo: SyncAdapterType {name=a, type=b, packageName=example.sync, x=y}").single().targetPackage)
        assertEquals(10123, snapshot(WakeCommand.BATTERY, "9,10123,l,pr,example.app:push,120,30,0,0").single().targetUid)
    }

    @Test fun givenEachCommandOutputShape_whenParsedOnAndroid_thenEveryPatternCompilesAndMatches() {
        assertEquals(listOf(0), WakeSystemEvidence.users("Users:\n  UserInfo{0:Owner:4c13} running"))
        assertEquals(12L, WakeSystemEvidence.packages("package:/data/app/a/base.apk=example.app versionCode:12 uid:10123").single().version)
        val log = "  1795365862.412  1000   581   621 I am_proc_start: [0,1,10236,example.app,service,{example.app/.Sync}]"
        assertEquals(1, WakeSystemEvidence.logcat(log, 1_795_365_900_000L, 100_000_000_000L).size)
        assertEquals(1_795_365_862_412L, WakeSystemEvidence.eventLogHead(log))
        assertEquals(mapOf("example.app" to 50), WakeSystemEvidence.standbyBuckets("example.app: 50"))
        assertEquals(setOf("example.app"), WakeSystemEvidence.queriedPackages("example.app\nNo operations."))
        assertEquals(setOf("user"), WakeSystemEvidence.idleWhitelist("user,example.app,10123").getValue("example.app"))
        assertEquals(setOf("android.intent.action.TIME_SET"), WakeSystemEvidence.protectedBroadcasts("Protected broadcast actions:\n  android.intent.action.TIME_SET"))
        val startInfo = """
              package: example.app
                Historical Process Start for userId=10123
                    ApplicationStartInfo #0:
             pid=0 realUid=10123 packageUid=10123 definingUid=10123 user=0
             package=example.app process=example.app startupState=0 reason=BROADCAST startType=COLD launchMode=0 wasForceStopped=false
             intent=Intent { act=android.intent.action.TIME_SET cmp=example.app/.Receiver }
             timestamps: 0=1000 1=2000
        """
        val record = WakeStartInfo.parse(startInfo, 5_000, 5_000, 1, setOf("android.intent.action.TIME_SET")).single()
        assertEquals("true", record.attributes["protected_broadcast"])
        assertEquals("example.app/.Receiver", record.component)
        val period = WakeProcStats.parse("vers,5\nperiod,2026-01-01-00-00-00,0,10,partial\npkgproc,a.b,10001,1,,0ns:5\npkgasc,a.b,10001,1,.S,system,1000,1,7,1,s:3\ntotal,0n:10", 35, ZoneOffset.UTC)
        assertEquals(7L, period.associations.single().boundMillis)
        assertEquals(5, WakeProcStats.committedCount("Only have 5 data sets"))
        assertEquals("<redacted>", wakeAction("content://secret/path"))
        assertEquals("sync-# #", normalizeWakeTag("sync-123e4567-e89b-12d3-a456-426614174000 1a2b3c4d"))
        assertEquals("PEER_SERVICE|a|b.c|b.c.S", wakePathKey("PEER_SERVICE", "a", "b.c", "b.c/.S"))
    }

    @Test fun givenLiveSnapshots_whenParsedOnTheDevice_thenEverySnapshotParserSucceeds() {
        val commands = listOf(
            WakeCommand.SERVICES, WakeCommand.PROVIDERS, WakeCommand.PROCESSES, WakeCommand.INTENTS, WakeCommand.COMPANIONS,
            WakeCommand.WALLPAPER, WakeCommand.ROLES, WakeCommand.WIDGETS, WakeCommand.JOBS, WakeCommand.ALARMS, WakeCommand.SYNCS,
            WakeCommand.NOTIFICATIONS,
        )
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in commands) {
            val argv = command.arguments(0, android.os.Process.myUid(), 0).joinToString(" ")
            val output = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(argv)).bufferedReader().use { it.readText() }
            WakeSystemEvidence.snapshot(command, output, SystemClock.elapsedRealtimeNanos())
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val argv = WakeCommand.ALARMS.arguments(0, android.os.Process.myUid(), 0).joinToString(" ")
            val output = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(argv)).bufferedReader().use { it.readText() }
            if ("operation=PendingIntent" in output) assertTrue(WakeSystemEvidence.snapshot(WakeCommand.ALARMS, output, 1).isNotEmpty())
        }
    }
}
