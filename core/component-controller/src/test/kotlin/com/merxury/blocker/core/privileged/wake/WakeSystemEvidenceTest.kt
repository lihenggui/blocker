// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.wake.WakeObservation
import org.junit.Test

class WakeSystemEvidenceTest {
    @Test fun givenSeveralBindings_whenReadingClients_thenEachKeepsItsOwnFullAction() {
        val dump = """
            * ServiceRecord{abc u0 remote.pkg/.Service}
              intent={act=unrelated.WAKE cmp=remote.pkg/.Service}
              app=ProcessRecord{abc 33:remote.pkg/u0a22}
              Bindings:
              * IntentBindRecord{aaa CREATE}:
                intent={act=sdk.peer.WAKE cmp=remote.pkg/.Service}
                * Client AppBindRecord{aaa ProcessRecord{abc 44:first.pkg/u0a11}}
              * IntentBindRecord{bbb CREATE}:
                intent={act=other.peer.WAKE cmp=remote.pkg/.Service}
                * Client AppBindRecord{bbb ProcessRecord{abc 55:second.pkg/u0a12}}
              * IntentBindRecord{ccc CREATE}:
                intent={cmp=remote.pkg/.Service}
                * Client AppBindRecord{ccc ProcessRecord{abc 66:third.pkg/u0a13}}
        """.trimIndent()

        val bindings = WakeSystemEvidence.snapshot(WakeCommand.SERVICES, dump, 100)

        assertThat(bindings.associate { it.callerUid to it.action }).containsExactly(
            10011,
            "sdk.peer.WAKE",
            10012,
            "other.peer.WAKE",
            10013,
            null,
        )
        assertThat(bindings.map { it.targetUid }.distinct()).containsExactly(10022)
        assertThat(bindings.map { it.attributes["host_process"] }.distinct()).containsExactly("remote.pkg")
        val provider = "* ContentProviderRecord{abcdef u0 example.app/.Provider}\n  proc=ProcessRecord{abc 23:example.app:data/u0a170}\n  Connections:\n    -> 24:example.client/u0a171 s1/1 u0/0"
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.PROVIDERS, provider, 100).single().attributes["host_process"]).isEqualTo("example.app:data")
    }

    @Test fun givenIncompleteUserList_whenParsingProfiles_thenNoProfileScopeIsReturned() {
        val owner = "Users:\n  UserInfo{0:Owner:4c13} running"
        assertThat(WakeSystemEvidence.users(owner)).containsExactly(0)
        assertThat(WakeSystemEvidence.users("$owner\n  UserInfo{10:Work:30}")).containsExactly(0, 10)
        assertThat(WakeSystemEvidence.users("$owner\n  <profile unavailable>")).isEmpty()
    }

    @Test fun givenMixedLogHeaders_whenParsingStarts_thenOnlySystemEventsAreAccepted() {
        val payload = "am_proc_start: [0,1419,10144,example.app,service,{example.app/.Sync}]"
        val lines = listOf(
            "241.653 10144 615 653 I $payload",
            "241.653 615 653 I $payload",
            "241.653 1000 615 653 I other: $payload",
            "241.653 system 615 653 I $payload",
        )
        assertThat(WakeSystemEvidence.logcat(lines.joinToString("\n"), 242000, 242000000000L).map { it.kind }).containsExactly("am_proc_start")
    }

    @Test fun givenDeepSleep_whenImportingEpochEvents_thenWallTimesAndOrderArePreserved() {
        val wall = 1_700_000_000_000L
        val elapsed = 5_400_000_000_000L
        val tag = "system 615 653 I am_proc_start: [0,1419,10144,example.app,service,{example.app/.Sync}]"
        val log = listOf("1699994599.000", "1699996340.000", "1699999940.000", "1700000001.000")
            .joinToString("\n") { "$it $tag" }
        val first = WakeSystemEvidence.logcat(log, wall, elapsed)
        assertThat(first.map { it.elapsedNanos }).containsExactly(1_740_000_000_000L, 5_340_000_000_000L).inOrder()
        assertThat(first.last().eventWallNanos).isEqualTo((wall - 60_000) * 1_000_000)
        val repeated = WakeSystemEvidence.logcat(log, wall + 1, elapsed + 1_200_000)
        assertThat(repeated.map { it.eventWallNanos }).containsExactlyElementsIn(first.map { it.eventWallNanos }).inOrder()
    }

    @Test fun givenSystemSnapshots_whenExtractingEvidence_thenOnlyImmediateSourcesAndRedactedActionsRemain() {
        val sample = "* ContentProviderRecord{abcdef u0 example.app/.Provider}\n  Connections:\n    -> 23:example.client/u0a170 s1/1 u0/0"
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.PROVIDERS, sample, 100).single().attributes["client_process"]).isEqualTo("example.client")
        val pending = "#0: PendingIntentRecord{123 example.client broadcastIntent}\n uid=10123 packageName=example.client\n requestIntent=act=example.UPDATE dat=content://secret/path cmp=example.app/.Receiver"
        val intent = WakeSystemEvidence.snapshot(WakeCommand.INTENTS, pending, 100).single()
        assertThat(intent.callerUid).isEqualTo(10123)
        assertThat(intent.toString()).doesNotContain("secret")
        val privateAction = pending.replace("example.UPDATE", "content://private/action")
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.INTENTS, privateAction, 100).single().action).isEqualTo("<redacted>")
        val start = WakeSystemEvidence.logcat("  241.653  1000   615   653 I am_proc_start: [0,1419,10144,example.app,service,{example.app/.Sync}]", 242000, 242000000000L).single()
        assertThat(start.elapsedNanos).isEqualTo(241653000000L)
        assertThat(start.eventWallNanos).isEqualTo(241653000000L)
        assertThat(start.callerUid).isNull()
    }

    @Test fun givenNotificationPreferences_whenParsing_thenOnlyAppsThatMayNotNotifyRemain() {
        // Android 15, then the Android 8 line of an app the user blocked.
        val dump = """
              AppSettings: example.allowed (10175) importance=DEFAULT userSet=false fixedImportance=true
              AppSettings: example.denied (10193) importance=NONE userSet=false
              AppSettings: example.unasked (10109)
              AppSettings: example.blocked (1010120) importance=NONE priority=0 visibility=-1000 showBadge=true
              AppSettings: example.none (10121) importance=NONEXISTENT
              AppSettings: example.sibling (10175)
        """.trimIndent()
        fun off(sdk: Int) = WakeSystemEvidence.snapshot(WakeCommand.NOTIFICATIONS, dump, 7, sdk = sdk).single().also { assertThat(it.kind).isEqualTo(WAKE_NOTIFICATIONS_OFF) }
            .attributes.getValue("apps").split(',')
        assertThat(off(32)).containsExactly("example.denied/10193", "example.blocked/1010120")
        // From Android 13 an app without an importance does not request the permission.
        assertThat(off(33)).containsExactly("example.denied/10193", "example.unasked/10109", "example.blocked/1010120")
        // A snapshot in which every app may notify is still a snapshot; another dump is not.
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.NOTIFICATIONS, "AppSettings: example.allowed (10175) importance=LOW", 7, sdk = 35).single().attributes["apps"]).isEmpty()
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.NOTIFICATIONS, "Can't find service: notification", 7, sdk = 35)).isEmpty()
    }

    @Test fun givenOverlayAndRegularWindows_whenParsing_thenOnlyVisibleOverlayPackagesAreRecorded() {
        val dump = """
            WINDOW MANAGER WINDOWS (dumpsys window windows)
              Window #0 Window{aaaa u0 private title}:
                mOwnerUid=10123 showForAllUsers=false package=example.zfloat appop=SYSTEM_ALERT_WINDOW
                mAttrs={(0,0) ty=APPLICATION_OVERLAY fmt=TRANSLUCENT}
                mHasSurface=true isReadyForDisplay()=true
              Window #1 Window{bbbb u10 example.alarm/.Window}:
                mAttrs={ty=2038}
                mViewVisibility=0x0
                isOnScreen=true
              Window #2 Window{cccc u0 example.hidden}:
                mAttrs={ty=TYPE_SYSTEM_ALERT}
                mHasSurface=true mViewVisibility=0x8
                isVisible=true
              Window #3 Window{dddd u0 example.no.surface}:
                mAttrs={ty=2006}
                mHasSurface=false mViewVisibility=0x0
                isVisible=true
              Window #4 Window{eeee u0 example.regular/.Main}:
                mAttrs={ty=BASE_APPLICATION}
                mHasSurface=true
              Window #5 Window{ffff u0 unrelated.title}:
                ownerPackageName=example.legacy
                mAttrs={ty=PHONE}
                mHasSurface=true
              Window #6 Window{gggg u0 example.offscreen}:
                mAttrs={ty=2002}
                mHasSurface=true mViewVisibility=0x0
                isOnScreen=false
              Window #7 Window{hhhh u0 example.zfloat}:
                mOwnerUid=10123
                mAttrs={ty=TYPE_APPLICATION_OVERLAY}
                mHasSurface=true
              Window #8 Window{iiii u0 example.incomplete}:
                mAttrs={ty=SYSTEM_OVERLAY}
              Unrelated section:
                mHasSurface=true isVisible=true
        """.trimIndent()

        val snapshot = WakeSystemEvidence.snapshot(WakeCommand.OVERLAYS, dump, 7).single()

        assertThat(snapshot.kind).isEqualTo(WAKE_OVERLAY_WINDOWS)
        assertThat(snapshot.elapsedNanos).isEqualTo(7)
        assertThat(snapshot.attributes).containsExactly("packages", "example.alarm,example.legacy,example.zfloat/10123")
    }

    @Test fun givenNoVisibleOverlay_whenParsing_thenOnlyRecognizedWindowsProduceEmptyEvidence() {
        val dump = """
            Window #0 Window{aaaa u0 example.hidden}:
              mAttrs={ty=2003}
              mHasSurface=false
        """.trimIndent()

        assertThat(WakeSystemEvidence.snapshot(WakeCommand.OVERLAYS, dump, 7).single().attributes).containsExactly("packages", "")
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.OVERLAYS, "Permission Denial", 7)).isEmpty()
    }

    @Test fun givenPlaybackTracks_whenParsing_thenOnlyUidsWithAnUnmutedStartedTrackAreRecorded() {
        val dump = """
            PlaybackActivityMonitor dump time: 08:12:00
              players:
             AudioPlaybackConfiguration piid:1 deviceIds:[1] type:AAudio u/pid:10130/11 state:started mutedState:none
             AudioPlaybackConfiguration piid:2 u/pid:10130/12 state:started
             AudioPlaybackConfiguration piid:3 u/pid:10110/13 state:started vol:0.25
            (not logged) AudioPlaybackConfiguration piid:4 u/pid:1010120/14 state:started
             AudioPlaybackConfiguration piid:5 u/pid:10122/15 state:idle
             AudioPlaybackConfiguration piid:6 u/pid:10123/16 state:paused
             AudioPlaybackConfiguration piid:7 u/pid:10124/17 state:started muted:true
             AudioPlaybackConfiguration piid:8 u/pid:10125/18 state:started vol:0.0
             AudioPlaybackConfiguration piid:9 u/pid:10126/19 state:started volume:0.00
             AudioPlaybackConfiguration piid:10 u/pid:10127/20 state:started mutedState:clientVolume
             AudioPlaybackConfiguration piid:11 u/pid:1000/21 state:started
             AudioPlaybackConfiguration piid:12 u/pid:99000/22 state:started
              ducked players piids:
            04-03 08:11:00 new player piid:1 AudioPlaybackConfiguration u/pid:10128/23 state:started
        """.trimIndent()

        val snapshot = WakeSystemEvidence.snapshot(WakeCommand.AUDIO, dump, 8).single()

        assertThat(snapshot.kind).isEqualTo(WAKE_AUDIO_PLAYBACK)
        assertThat(snapshot.elapsedNanos).isEqualTo(8)
        assertThat(snapshot.attributes).containsExactly("uids", "10110,10130,1010120")
    }

    @Test fun givenNoActivePlayer_whenParsing_thenOnlyRecognizedPlaybackProducesEmptyEvidence() {
        val dump = "PlaybackActivityMonitor dump time: 08:12:00\n\n  players:\n\n  ducked players piids:"
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.AUDIO, dump, 8).single().attributes).containsExactly("uids", "")
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.AUDIO, "Audio event log:", 8)).isEmpty()
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.AUDIO, "players:\n AudioPlaybackConfiguration piid:1 state:started", 8)).isEmpty()
    }

    @Test fun givenLegacyAudioPlayers_whenParsing_thenStartedPlaybackIsRecorded() {
        val dump = """
            PlaybackActivityMonitor dump time: 08:12:00
              players:
                ID:123 -- type:android.media.AudioTrack u/pid:10123/456 state:started
                ID:124 -- type:android.media.AudioTrack u/pid:10124/457 state:paused
              ducked players piids: []
                ID:125 -- type:android.media.AudioTrack u/pid:10125/458 state:started
        """.trimIndent()

        assertThat(WakeSystemEvidence.snapshot(WakeCommand.AUDIO, dump, 8).single().attributes).containsExactly("uids", "10123")
    }

    @Test fun givenUnrecognizedPlayerRows_whenParsing_thenPlaybackEvidenceIsUnavailable() {
        for (row in listOf("  unknown player format", "  ID:123 -- type:android.media.AudioTrack", "  ID:123 -- state:started")) {
            val dump = "players:\n  ID:124 -- u/pid:10124/457 state:started\n$row"
            assertThat(WakeSystemEvidence.snapshot(WakeCommand.AUDIO, dump, 8)).isEmpty()
        }
    }

    @Test fun givenLegacyAlarmDump_whenParsing_thenThePendingAlarmIsCaptured() {
        val dump = """
            Current Alarm Manager state:
              Pending alarm batches: 1
                RTC_WAKEUP #0: Alarm{123 type 0 when 1533371951093 example.app}
                  operation=PendingIntent{abc: PendingIntentRecord{beef example.app broadcastIntent}}
        """.trimIndent()

        val alarm = WakeSystemEvidence.snapshot(WakeCommand.ALARMS, dump, 7).single()

        assertThat(alarm.kind).isEqualTo("PendingAlarm")
        assertThat(alarm.callerPackage).isEqualTo("example.app")
        assertThat(alarm.attributes).containsExactly("record", "beef", "creator_package", "example.app")
    }

    @Test fun givenRecognizedEmptyAlarmDump_whenParsing_thenAnEmptySnapshotSupersedesOlderAlarms() {
        val dump = "Current Alarm Manager state:\n  Pending alarm batches: 0"
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.ALARMS, dump, 7)).containsExactly(WakeObservation("PendingAlarm", 7))
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.ALARMS, "alarm service unavailable", 7)).isEmpty()
    }

    @Test fun givenRoleAndJobDumps_whenDispatchingSnapshots_thenEachParserUsesOnlyItsOwnFields() {
        val role = "user_id=10\nname=android.app.role.HOME\nholders=[example.home]"
        val job = "JOB #u0a12/17: example.sync/.JobService"

        val holder = WakeSystemEvidence.snapshot(WakeCommand.ROLES, role, 5).single()
        val scheduled = WakeSystemEvidence.snapshot(WakeCommand.JOBS, job, 6).single()

        assertThat(holder.targetPackage).isEqualTo("example.home")
        assertThat(holder.attributes["user"]).isEqualTo("10")
        assertThat(scheduled.component).isEqualTo("example.sync/.JobService")
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.JOBS, role, 5)).isEmpty()
    }
}
