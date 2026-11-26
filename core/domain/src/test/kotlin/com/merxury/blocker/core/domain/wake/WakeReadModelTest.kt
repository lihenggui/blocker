// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeFindingDay
import com.merxury.blocker.core.database.wake.WakeVerdictDay
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.wake.AppWakeActivity
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.HOLD_KIND_PREFIX
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.model.wake.wakePathKey
import org.junit.Test

class WakeReadModelTest {
    @Test fun givenPeerRevivesAndHolds_whenBuildingActivity_thenDirectionsMergeAndPeersSortByDirectionCountAndRecency() {
        val findings = listOf(
            revive(ReviveTrigger.PEER_SERVICE, actor = "mutual", detail = "$APP/.Service", count = 2, afterKill = "empty"),
            hold(HoldCause.PROVIDER_CLIENT, target = "mutual", actor = APP, detail = ".Provider"),
            revive(ReviveTrigger.PEER_BROADCAST, actor = "high.count", count = 5),
            hold(HoldCause.BOUND_BY_PEER, actor = "old", seenNanos = 4_000_000),
            hold(HoldCause.PROVIDER_CLIENT, actor = "recent", seenNanos = 5_000_000),
            revive(ReviveTrigger.PEER_PROVIDER, target = "outbound", actor = APP, count = 8),
            revive(ReviveTrigger.PEER_SERVICE, actor = "android"),
            revive(ReviveTrigger.PEER_SERVICE, actor = "10035"),
            revive(ReviveTrigger.PEER_SERVICE, actor = APP),
            revive(ReviveTrigger.PEER_SERVICE, actor = null),
            revive(ReviveTrigger.PEER_SERVICE, target = "android", actor = APP),
            hold(HoldCause.PROVIDER_CLIENT, target = "10035", actor = APP),
            revive(ReviveTrigger.PEER_SERVICE, target = "unrelated", actor = "stranger"),
        )

        val peers = activity(findings).peers

        assertThat(peers.map { it.peer to it.direction }).containsExactly(
            "mutual" to WakeDirection.MUTUAL,
            "high.count" to WakeDirection.INBOUND,
            "recent" to WakeDirection.INBOUND,
            "old" to WakeDirection.INBOUND,
            "outbound" to WakeDirection.OUTBOUND,
        ).inOrder()
        val mutual = peers.first()
        val incoming = mutual.inbound.single()
        assertThat(incoming.trigger).isEqualTo(ReviveTrigger.PEER_SERVICE)
        assertThat(incoming.afterKill).isTrue()
        assertThat(incoming.blockMethod).isEqualTo(WakeBlockMethod.FIREWALL_RULE)
        assertThat(mutual.outbound.single().hold).isEqualTo(HoldCause.PROVIDER_CLIENT)
    }

    @Test fun givenOwnRevivesAndHolds_whenBuildingActivity_thenEveryKindGroupsItsPathsAndSortsByCount() {
        val findings = listOf(
            revive(ReviveTrigger.STICKY_RESTART, detail = ".Restarted", count = 2),
            revive(ReviveTrigger.UNKNOWN, afterKill = "empty"),
            revive(ReviveTrigger.SYSTEM_BIND, afterKill = "empty"),
            revive(ReviveTrigger.ALARM, detail = "restart-tag", afterKill = "empty"),
            revive(ReviveTrigger.SELF_PROCESS, actor = APP, detail = ".Own", count = 3),
            revive(ReviveTrigger.SELF_PROCESS, actor = APP, detail = "$APP/$APP.Own", count = 2, day = 11),
            revive(ReviveTrigger.SELF_PROCESS, actor = APP, detail = ".Own", count = 1, afterKill = "empty"),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.CONNECTIVITY", count = 6),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.BOOT", count = 4),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = null, detail = "test.BOOT", count = 1),
            revive(ReviveTrigger.ALARM, detail = "alarm-tag"),
            revive(ReviveTrigger.JOB, detail = "job-name"),
            revive(ReviveTrigger.SYNC),
            revive(ReviveTrigger.PENDING_INTENT),
            hold(HoldCause.FOREGROUND_SERVICE, detail = ".Busy", screenOff = 18, count = 4),
            hold(HoldCause.FOREGROUND_SERVICE, detail = ".Busy", actor = "dataSync", screenOff = 6),
            hold(HoldCause.WAKELOCK, screenOff = 7),
            hold(HoldCause.RUNNING_JOB, screenOff = 8),
            hold(HoldCause.BOUND_BY_PEER, actor = APP, detail = ".Busy", screenOff = 9),
            hold(HoldCause.FOREGROUND_SERVICE, detail = ".ScreenOn"),
            hold(HoldCause.BOUND_BY_SYSTEM, actor = "android", screenOff = 3),
            hold(HoldCause.OVERLAY_WINDOW, screenOff = 4),
            hold(HoldCause.UNKNOWN, screenOff = 5),
            revive(ReviveTrigger.UNKNOWN),
            revive(ReviveTrigger.SYSTEM_BIND),
        )

        val activity = activity(findings)
        val methods = activity.own.groupBy { it.kind }

        assertThat(activity.backgroundStarts).isEqualTo(findings.filter { it.trigger != null }.sumOf { it.count })
        assertThat(methods.keys).containsExactlyElementsIn(OwnWakeKind.entries).inOrder()
        val restarts = methods.getValue(OwnWakeKind.RESTART_AFTER_CLOSE).flatMap { it.paths }
        assertThat(restarts.map { it.trigger }).containsExactly(
            ReviveTrigger.STICKY_RESTART,
            ReviveTrigger.UNKNOWN,
            ReviveTrigger.SYSTEM_BIND,
            ReviveTrigger.ALARM,
            ReviveTrigger.SELF_PROCESS,
        )
        assertThat(restarts.single { it.trigger == ReviveTrigger.ALARM }.blockMethod).isEqualTo(WakeBlockMethod.BACKGROUND_RESTRICTION)
        assertThat(restarts.single { it.trigger == ReviveTrigger.SELF_PROCESS }.count).isEqualTo(1)
        assertThat(methods.getValue(OwnWakeKind.SELF_START).single().paths.single().count).isEqualTo(5)
        val systemEvents = methods.getValue(OwnWakeKind.SYSTEM_EVENT)
        assertThat(systemEvents.map { it.action }).containsExactly("test.CONNECTIVITY", "test.BOOT").inOrder()
        assertThat(systemEvents.last().paths).hasSize(2)
        val timed = methods.getValue(OwnWakeKind.TIMED).single()
        assertThat(timed.component).isNull()
        assertThat(timed.paths.map { it.trigger }).containsExactly(ReviveTrigger.ALARM, ReviveTrigger.JOB, ReviveTrigger.SYNC, ReviveTrigger.PENDING_INTENT)
        val screenOff = methods.getValue(OwnWakeKind.SCREEN_OFF_RUN)
        assertThat(screenOff.flatMap { it.paths }.map { it.hold }).containsExactly(
            HoldCause.FOREGROUND_SERVICE,
            HoldCause.FOREGROUND_SERVICE,
            HoldCause.WAKELOCK,
            HoldCause.RUNNING_JOB,
            HoldCause.BOUND_BY_PEER,
            HoldCause.OVERLAY_WINDOW,
            HoldCause.UNKNOWN,
        )
        assertThat(screenOff.first().paths).hasSize(2)
        assertThat(screenOff.filter { it.component == "$APP.Busy" }).hasSize(2)
    }

    @Test fun givenNamedAndUnnamedPaths_whenBuildingActivity_thenBlockMethodsFollowTheTriggerAndHold() {
        val firewall = WakeBlockMethod.FIREWALL_RULE
        val component = WakeBlockMethod.COMPONENT
        val background = WakeBlockMethod.BACKGROUND_RESTRICTION
        val none = WakeBlockMethod.NONE
        val revives = listOf(
            revive(ReviveTrigger.PEER_SERVICE, actor = PEER, detail = ".Service") to firewall,
            revive(ReviveTrigger.PEER_BROADCAST, actor = PEER, detail = "test.BOOT") to component,
            revive(ReviveTrigger.PEER_BROADCAST, actor = PEER, detail = "$APP/.Receiver") to component,
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.BOOT") to component,
            revive(ReviveTrigger.SELF_PROCESS, actor = APP, detail = ".Service") to none,
            revive(ReviveTrigger.SELF_PROCESS, actor = APP, detail = "custom/WAKE") to component,
            revive(ReviveTrigger.PEER_PROVIDER, actor = PEER, detail = ".Provider") to component,
            revive(ReviveTrigger.STICKY_RESTART, detail = ".Service") to component,
            revive(ReviveTrigger.ALARM) to background,
            revive(ReviveTrigger.JOB) to background,
            revive(ReviveTrigger.SYNC) to background,
            revive(ReviveTrigger.PENDING_INTENT) to background,
            revive(ReviveTrigger.PEER_SERVICE, actor = PEER) to none,
            revive(ReviveTrigger.PEER_SERVICE, actor = PEER, detail = "other/.Service") to none,
            revive(ReviveTrigger.PEER_BROADCAST, actor = PEER) to none,
            revive(ReviveTrigger.PEER_PROVIDER, actor = PEER) to none,
            revive(ReviveTrigger.STICKY_RESTART) to none,
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "$APP/.Receiver") to component,
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android") to none,
            revive(ReviveTrigger.SELF_PROCESS, actor = APP) to none,
            revive(ReviveTrigger.UNKNOWN, afterKill = "empty") to none,
        )
        val holds = listOf(
            hold(HoldCause.BOUND_BY_PEER, actor = PEER, detail = ".Service") to firewall,
            hold(HoldCause.PROVIDER_CLIENT, actor = PEER, detail = ".Provider") to component,
            hold(HoldCause.FOREGROUND_SERVICE, detail = ".Service", screenOff = 2) to component,
            hold(HoldCause.FOREGROUND_SERVICE, screenOff = 2) to background,
            hold(HoldCause.WAKELOCK, screenOff = 2) to background,
            hold(HoldCause.RUNNING_JOB, screenOff = 2) to background,
            hold(HoldCause.BOUND_BY_PEER, actor = APP, detail = ".Service", screenOff = 2) to firewall,
            hold(HoldCause.BOUND_BY_PEER, actor = PEER) to none,
            hold(HoldCause.PROVIDER_CLIENT, actor = PEER) to none,
        )

        for ((row, expected) in revives + holds) {
            val result = activity(listOf(row))
            val paths = result.own.flatMap { it.paths } + result.peers.flatMap { it.inbound }
            assertThat(paths.single().blockMethod).isEqualTo(expected)
        }
    }

    @Test fun givenNamedPeerServiceAndNonAppActor_whenMappingWakePath_thenNoBlockMethodIsOffered() {
        val actorsWithoutAPackage = listOf(null, "android", "10035")
        val paths = actorsWithoutAPackage.map { actor -> revive(ReviveTrigger.PEER_SERVICE, actor = actor, detail = ".Service").toWakePath() }

        assertThat(paths.map { it.component }).containsExactlyElementsIn(actorsWithoutAPackage.map { "$APP.Service" })
        assertThat(paths.map { it.blockMethod }.toSet()).containsExactly(WakeBlockMethod.NONE)
    }

    @Test fun givenComponentAndBroadcastDetails_whenBuildingActivity_thenTheyRetainTheirTypesAndIdentities() {
        val rows = listOf(
            revive(ReviveTrigger.PEER_BROADCAST, actor = PEER, detail = "$APP/.Receiver"),
            revive(ReviveTrigger.PEER_BROADCAST, actor = PEER, detail = "test.BOOT"),
            revive(ReviveTrigger.PEER_SERVICE, actor = PEER, detail = ".Service"),
            hold(HoldCause.PROVIDER_CLIENT, actor = PEER, detail = "external.pkg.Provider"),
        )
        val paths = activity(rows).peers.single().inbound.associateBy { it.pathKey }

        assertThat(paths.getValue(rows[0].pathKey).component).isEqualTo("$APP.Receiver")
        assertThat(paths.getValue(rows[0].pathKey).action).isNull()
        val action = paths.getValue(rows[1].pathKey)
        assertThat(action.componentType).isEqualTo(ComponentType.RECEIVER)
        assertThat(action.component).isNull()
        assertThat(action.action).isEqualTo("test.BOOT")
        val service = paths.getValue(rows[2].pathKey)
        assertThat(service.componentType).isEqualTo(ComponentType.SERVICE)
        assertThat(service.component).isEqualTo("$APP.Service")
        val provider = paths.getValue(rows[3].pathKey)
        assertThat(provider.componentType).isEqualTo(ComponentType.PROVIDER)
        assertThat(provider.component).isEqualTo("external.pkg.Provider")
    }

    @Test fun givenOpaqueAlarmJobAndWakelockTags_whenBuildingActivity_thenTheyNeverBecomeComponents() {
        val digest = "1234567890abcdef12345678"
        val rows = listOf(
            revive(ReviveTrigger.ALARM, detail = "com.example.sync.alarm"),
            revive(ReviveTrigger.JOB, detail = digest),
            revive(ReviveTrigger.SYNC, detail = digest),
            hold(HoldCause.WAKELOCK, detail = "com.example:lock", screenOff = 3),
            hold(HoldCause.RUNNING_JOB, detail = digest, screenOff = 4),
        )

        val paths = activity(rows).own.flatMap { it.paths }

        assertThat(paths).hasSize(5)
        assertThat(paths.map { it.component }.toSet()).containsExactly(null)
        assertThat(paths.map { it.componentType }.toSet()).containsExactly(null)
        assertThat(paths.map { it.blockMethod }.toSet()).containsExactly(WakeBlockMethod.BACKGROUND_RESTRICTION)
    }

    @Test fun givenNamedAndUnnamedScheduledServices_whenBuildingActivity_thenNamedServicesAreIndividuallyBlockable() {
        val rows = listOf(
            revive(ReviveTrigger.JOB, detail = "$APP/.FirstJob"),
            revive(ReviveTrigger.JOB, detail = "$APP/$APP.FirstJob", day = 11),
            revive(ReviveTrigger.JOB, detail = "$APP.SecondJob"),
            revive(ReviveTrigger.SYNC, detail = ".SyncAdapter"),
            revive(ReviveTrigger.JOB),
            revive(ReviveTrigger.ALARM),
            hold(HoldCause.RUNNING_JOB, detail = "$APP/.FirstJob", screenOff = 4),
        )

        val methods = activity(rows).own

        val timed = methods.filter { it.kind == OwnWakeKind.TIMED }.associateBy { it.component }
        assertThat(timed.keys).containsExactly("$APP.FirstJob", "$APP.SecondJob", "$APP.SyncAdapter", null)
        assertThat(timed.getValue("$APP.FirstJob").paths.single().count).isEqualTo(2)
        assertThat(timed.getValue(null).paths.map { it.trigger }).containsExactly(ReviveTrigger.JOB, ReviveTrigger.ALARM)
        val running = methods.single { it.kind == OwnWakeKind.SCREEN_OFF_RUN }
        assertThat(running.component).isEqualTo("$APP.FirstJob")
        for (path in methods.filter { it.component != null }.flatMap { it.paths }) {
            assertThat(path.componentType).isEqualTo(ComponentType.SERVICE)
            assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        }
    }

    @Test fun givenStealthActivityPaths_whenBuildingActivity_thenBothAreBlockableWithSeparateOwnAndPeerAttribution() {
        val findings = listOf(
            revive(ReviveTrigger.PEER_ACTIVITY, actor = APP, detail = ".Pixel"),
            revive(ReviveTrigger.PEER_ACTIVITY, actor = PEER, detail = "$APP/.Popup"),
            revive(ReviveTrigger.PEER_ACTIVITY, actor = "example.launcher", detail = ".Main", justification = Justification.USER_INITIATED),
        )

        val result = activity(findings)

        assertThat(result.justified.userInitiated).isEqualTo(1)
        assertThat(result.backgroundStarts).isEqualTo(2)
        val own = result.own.single()
        assertThat(own.kind).isEqualTo(OwnWakeKind.SELF_START)
        assertThat(own.component).isEqualTo("$APP.Pixel")
        val peer = result.peers.single()
        assertThat(peer.peer).isEqualTo(PEER)
        assertThat(peer.direction).isEqualTo(WakeDirection.INBOUND)
        assertThat(peer.inbound.single().component).isEqualTo("$APP.Popup")
        assertThat(buildAppWakeActivity(PEER, 10, 11, findings, emptyList()).peers.single().direction).isEqualTo(WakeDirection.OUTBOUND)
        for (path in own.paths + peer.inbound) {
            assertThat(path.componentType).isEqualTo(ComponentType.ACTIVITY)
        }
        assertThat(own.paths.single().blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        assertThat(peer.inbound.single().blockMethod).isEqualTo(WakeBlockMethod.FIREWALL_RULE)
    }

    @Test fun givenOverlayHold_whenBuildingActivity_thenScreenOffTimeOffersBackgroundRestriction() {
        val result = activity(listOf(hold(HoldCause.OVERLAY_WINDOW, duration = 9, screenOff = 8)))

        assertThat(result.peers).isEmpty()
        assertThat(result.own.single().kind).isEqualTo(OwnWakeKind.SCREEN_OFF_RUN)
        val path = result.own.single().paths.single()
        assertThat(path.hold).isEqualTo(HoldCause.OVERLAY_WINDOW)
        assertThat(path.screenOffNanos).isEqualTo(8)
        assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.BACKGROUND_RESTRICTION)
        assertThat(activity(listOf(hold(HoldCause.OVERLAY_WINDOW))).own).isEmpty()
    }

    @Test fun givenUnknownHoldWithScreenOffTime_whenBuildingActivity_thenItExplainsTheVerdictAndOffersBackgroundRestriction() {
        val screenOff = WakeVerdictThresholds.KEEP_ALIVE_SCREEN_OFF_HOLD_NONE_NANOS
        val held = hold(HoldCause.UNKNOWN, duration = screenOff, screenOff = screenOff)
        val screenOn = held.copy(screenOffNanos = 0)
        val day = verdict(APP, 10, WakeVerdict.KEEP_ALIVE, screenOff = screenOff)

        val result = activity(listOf(held), listOf(day))

        assertThat(result.verdict).isEqualTo(WakeVerdict.KEEP_ALIVE)
        assertThat(result.own.single().kind).isEqualTo(OwnWakeKind.SCREEN_OFF_RUN)
        val path = result.own.single().paths.single()
        assertThat(path.hold).isEqualTo(HoldCause.UNKNOWN)
        assertThat(path.screenOffNanos).isEqualTo(screenOff)
        assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.BACKGROUND_RESTRICTION)
        assertThat(activity(listOf(screenOn)).own).isEmpty()
    }

    @Test fun givenJustifiedStartsAndPush_whenBuildingActivity_thenOnlyRevivesOfThisAppCountAndPushTakesPrecedence() {
        val result = activity(
            listOf(
                revive(ReviveTrigger.PEER_SERVICE, actor = PEER, justification = Justification.USER_INITIATED, count = 2),
                revive(ReviveTrigger.SELF_PROCESS, actor = APP, detail = ".Service", justification = Justification.PRODUCTIVE, count = 3),
                revive(ReviveTrigger.SYSTEM_BIND, actor = "android", justification = Justification.USER_GRANTED_ROLE, count = 4),
                revive(ReviveTrigger.PUSH_TRANSPORT, actor = PEER, justification = Justification.PRODUCTIVE, count = 5),
                revive(ReviveTrigger.PUSH_TRANSPORT, actor = PEER, count = 6),
                revive(ReviveTrigger.PUSH_TRANSPORT, actor = APP, target = PEER, count = 7),
                revive(ReviveTrigger.PEER_SERVICE, actor = APP, target = PEER, justification = Justification.PRODUCTIVE, count = 8),
                hold(HoldCause.BOUND_BY_PEER, actor = PEER, justification = Justification.USER_GRANTED_ROLE, count = 9),
                hold(HoldCause.WAKELOCK, justification = Justification.PRODUCTIVE, screenOff = 10),
            ),
        )

        // The two starts the user initiated are not background starts.
        assertThat(result.backgroundStarts).isEqualTo(18)
        assertThat(result.justified).isEqualTo(JustifiedStarts(userInitiated = 2, notification = 3, push = 5, grantedRole = 4))
        assertThat(result.own.single().paths.single().count).isEqualTo(6)
        assertThat(result.peers).isEmpty()
        assertThat(result.verdict).isEqualTo(WakeVerdict.NORMAL)
    }

    @Test fun givenSilentPushDetails_whenBuildingActivity_thenComponentsAndActionsAreBlockableSystemEvents() {
        val action = "com.google.android.c2dm.intent.RECEIVE"
        val rows = listOf(
            revive(ReviveTrigger.PUSH_TRANSPORT, detail = action, afterKill = "empty"),
            revive(ReviveTrigger.PUSH_TRANSPORT, detail = "$APP/.PushReceiver"),
            revive(ReviveTrigger.PUSH_TRANSPORT, detail = "vendor.sdk.PushService"),
            revive(ReviveTrigger.PUSH_TRANSPORT, detail = "<redacted>"),
        )

        val methods = activity(rows).own

        assertThat(methods.map { it.kind }.toSet()).containsExactly(OwnWakeKind.SYSTEM_EVENT)
        val paths = methods.flatMap { it.paths }.associateBy { it.pathKey }
        val actionPath = paths.getValue(rows[0].pathKey)
        assertThat(actionPath.action).isEqualTo(action)
        assertThat(actionPath.component).isNull()
        assertThat(actionPath.componentType).isEqualTo(ComponentType.RECEIVER)
        assertThat(actionPath.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        assertThat(actionPath.afterKill).isTrue()
        for ((row, component) in listOf(rows[1] to "$APP.PushReceiver", rows[2] to "vendor.sdk.PushService")) {
            val path = paths.getValue(row.pathKey)
            assertThat(path.component).isEqualTo(component)
            assertThat(path.componentType).isNull()
            assertThat(path.action).isNull()
            assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        }
        assertThat(paths.getValue(rows[3].pathKey).blockMethod).isEqualTo(WakeBlockMethod.NONE)
    }

    @Test fun givenRepeatedPathsAndVerdictsAcrossDays_whenBuildingActivity_thenOnlyWindowDaysAreSummed() {
        val first = hold(HoldCause.BOUND_BY_PEER, actor = PEER, detail = ".Service", count = 2, duration = 40, screenOff = 5, seenNanos = 10_800_000)
        val latest = first.copy(day = 11, id = "day11", count = 3, durationNanos = 70, screenOffNanos = 15, lastSeenNanos = 12_999_999)
        val revive = revive(ReviveTrigger.PEER_SERVICE, actor = PEER, count = 2)
        val killed = revive.copy(day = 11, id = "killed", afterKill = "empty", count = 3)
        val result = activity(
            listOf(first, latest, first.copy(day = 9), first.copy(day = 12), revive, killed, revive.copy(day = 9)),
            listOf(verdict(APP, 9, WakeVerdict.KEEP_ALIVE), verdict(APP, 10), verdict(APP, 11, WakeVerdict.SUSPICIOUS), verdict(PEER, 11, WakeVerdict.KEEP_ALIVE), verdict(APP, 12, WakeVerdict.KEEP_ALIVE)),
        )

        assertThat(result.verdict).isEqualTo(WakeVerdict.SUSPICIOUS)
        assertThat(result.backgroundStarts).isEqualTo(5)
        val paths = result.peers.single().inbound.associateBy { it.pathKey }
        val binding = paths.getValue(first.pathKey)
        assertThat(binding.count).isEqualTo(5)
        assertThat(binding.durationNanos).isEqualTo(110)
        assertThat(binding.screenOffNanos).isEqualTo(20)
        assertThat(binding.lastSeenMillis).isEqualTo(12)
        val start = paths.getValue(revive.pathKey)
        assertThat(start.count).isEqualTo(5)
        assertThat(start.afterKill).isTrue()
    }

    @Test fun givenAllEvidenceKinds_whenBuildingOverview_thenTheFirstAvailableHeadlineWins() {
        val daily = listOf(verdict(APP, 10, none = 30, afterKill = 4, screenOff = 15), verdict(APP, 11, none = 20, afterKill = 2, screenOff = 10))
        val outbound = listOf(
            revive(ReviveTrigger.PEER_PROVIDER, target = PEER, actor = APP),
            revive(ReviveTrigger.PEER_PROVIDER, target = PEER, actor = APP, day = 11),
            hold(HoldCause.BOUND_BY_PEER, target = "other.app", actor = APP),
        )
        val incoming = listOf(
            revive(ReviveTrigger.PEER_SERVICE, actor = PEER, count = 3),
            revive(ReviveTrigger.PEER_BROADCAST, actor = PEER, count = 2),
            hold(HoldCause.PROVIDER_CLIENT, actor = "other.app", count = 4),
        )
        val events = listOf(
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.BOOT", count = 5),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.BOOT", count = 3, day = 11),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.OTHER", count = 6),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "$APP/.Receiver", count = 9),
        )
        val timed = listOf(revive(ReviveTrigger.ALARM, count = 2), revive(ReviveTrigger.JOB, count = 4))
        val excluded = listOf(
            revive(ReviveTrigger.PEER_SERVICE, target = PEER, actor = APP, justification = Justification.PRODUCTIVE),
            revive(ReviveTrigger.PEER_SERVICE, actor = "99999"),
            revive(ReviveTrigger.PUSH_TRANSPORT, actor = PEER),
            revive(ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "test.JUSTIFIED", justification = Justification.USER_INITIATED, count = 90),
            revive(ReviveTrigger.PEER_SERVICE, actor = PEER, day = 9),
            revive(ReviveTrigger.PEER_SERVICE, target = PEER, actor = APP, day = 12),
        )
        val withoutKill = daily.map { it.copy(afterKillNoneRevives = 0) }
        val withoutScreenOff = withoutKill.map { it.copy(screenOffHoldNoneNanos = 0) }

        fun headline(rows: List<WakeFindingDay>, days: List<WakeVerdictDay>) = overview(rows + excluded, days).single().headline
        assertThat(headline(outbound + incoming + events + timed, daily)).isEqualTo(WakeHeadline.RestartedAfterClose(6))
        assertThat(headline(outbound + incoming + events + timed, withoutKill)).isEqualTo(WakeHeadline.StartedOthers(2))
        assertThat(headline(incoming + events + timed, withoutKill)).isEqualTo(WakeHeadline.StartedByOthers(2, 9))
        assertThat(headline(events + timed, withoutKill)).isEqualTo(WakeHeadline.RanWithScreenOff(25))
        assertThat(headline(events + timed, withoutScreenOff)).isEqualTo(WakeHeadline.StartsOnSystemEvent("test.BOOT", 8))
        assertThat(headline(timed, withoutScreenOff)).isEqualTo(WakeHeadline.TimedWakeups(6))
        assertThat(headline(emptyList(), withoutScreenOff)).isEqualTo(WakeHeadline.BackgroundStarts(50))
    }

    @Test fun givenOnlySilentPushes_whenBuildingOverview_thenTheHeadlineCountsTheirBackgroundStarts() {
        val pushes = revive(ReviveTrigger.PUSH_TRANSPORT, detail = "com.google.android.c2dm.intent.RECEIVE", count = 12, afterKill = "empty")

        val result = overview(listOf(pushes), listOf(verdict(APP, 10))).single()

        assertThat(result.verdict).isEqualTo(WakeVerdict.NORMAL)
        assertThat(result.headline).isEqualTo(WakeHeadline.BackgroundStarts(12))
    }

    @Test fun givenMixedDailyVerdicts_whenBuildingOverview_thenOnlyAppsWithWindowVerdictsAppearInSeverityAndCountOrder() {
        val verdicts = listOf(
            verdict("normal", 10, none = 77),
            verdict("suspicious.one", 10, WakeVerdict.SUSPICIOUS, none = 99),
            verdict("suspicious.two", 10, WakeVerdict.SUSPICIOUS),
            verdict("suspicious.two", 11, WakeVerdict.SUSPICIOUS),
            verdict("keep.once.low", 10, WakeVerdict.KEEP_ALIVE, none = 2),
            verdict("keep.once.high", 10, WakeVerdict.KEEP_ALIVE, none = 1),
            verdict("keep.once.high", 11, none = 3),
            verdict("keep.with.suspicious", 10, WakeVerdict.KEEP_ALIVE),
            verdict("keep.with.suspicious", 11, WakeVerdict.SUSPICIOUS),
            verdict("keep.twice", 10, WakeVerdict.KEEP_ALIVE),
            verdict("keep.twice", 11, WakeVerdict.KEEP_ALIVE),
            verdict("suspicious.one", 9, WakeVerdict.KEEP_ALIVE),
            verdict("normal", 12, WakeVerdict.KEEP_ALIVE),
            verdict("old.app", 9, WakeVerdict.KEEP_ALIVE),
            verdict("future.app", 12, WakeVerdict.KEEP_ALIVE),
        )

        val rows = overview(listOf(revive(ReviveTrigger.ALARM, target = "finding.only")), verdicts)

        assertThat(rows).containsExactly(
            AppWakeSummary("keep.twice", WakeVerdict.KEEP_ALIVE, 2, 0, WakeHeadline.BackgroundStarts(0)),
            AppWakeSummary("keep.with.suspicious", WakeVerdict.KEEP_ALIVE, 1, 1, WakeHeadline.BackgroundStarts(0)),
            AppWakeSummary("keep.once.high", WakeVerdict.KEEP_ALIVE, 1, 0, WakeHeadline.BackgroundStarts(4)),
            AppWakeSummary("keep.once.low", WakeVerdict.KEEP_ALIVE, 1, 0, WakeHeadline.BackgroundStarts(2)),
            AppWakeSummary("suspicious.two", WakeVerdict.SUSPICIOUS, 0, 2, WakeHeadline.BackgroundStarts(0)),
            AppWakeSummary("suspicious.one", WakeVerdict.SUSPICIOUS, 0, 1, WakeHeadline.BackgroundStarts(99)),
            AppWakeSummary("normal", WakeVerdict.NORMAL, 0, 0, WakeHeadline.BackgroundStarts(77)),
        ).inOrder()
    }

    private fun activity(findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay> = emptyList()): AppWakeActivity = buildAppWakeActivity(APP, 10, 11, findings, verdicts)

    private fun overview(findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay>): List<AppWakeSummary> = buildWakeOverview(10, 11, findings, verdicts)

    private fun revive(
        trigger: ReviveTrigger,
        target: String = APP,
        actor: String? = null,
        detail: String? = null,
        justification: Justification = Justification.NONE,
        count: Long = 1,
        afterKill: String? = null,
        day: Long = 10,
    ): WakeFindingDay {
        val key = wakePathKey(trigger.name, actor, target, detail)
        return WakeFindingDay("$day|$key|${justification.name}|$afterKill", day, target, key, trigger.name, null, actor, detail, null, afterKill, justification.name, "DIRECT", null, 0, count, 0, 0, day * 1_000_000, 0)
    }

    private fun hold(
        cause: HoldCause,
        target: String = APP,
        actor: String? = null,
        detail: String? = null,
        justification: Justification = Justification.NONE,
        count: Long = 1,
        duration: Long = 10,
        screenOff: Long = 0,
        seenNanos: Long = 10_000_000,
    ): WakeFindingDay {
        val key = wakePathKey(HOLD_KIND_PREFIX + cause.name, actor, target, detail)
        return WakeFindingDay("10|$key|${justification.name}", 10, target, key, null, cause.name, actor, detail, null, null, justification.name, "DIRECT", null, 0, count, duration, screenOff, seenNanos, 0)
    }

    private fun verdict(
        target: String,
        day: Long,
        verdict: WakeVerdict = WakeVerdict.NORMAL,
        none: Long = 0,
        afterKill: Long = 0,
        screenOff: Long = 0,
    ) = WakeVerdictDay(day, target, verdict.name, none, none, afterKill, afterKill, 0, screenOff, 0, 0)

    private companion object {
        const val APP = "shown.app"
        const val PEER = "peer.app"
    }
}
