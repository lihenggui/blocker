// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.google.common.truth.Truth.assertThat
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
