// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeFindingTest {
    @Test fun givenShortAndLongComponentNames_whenBuildingPathKeys_thenTheyAggregate() {
        val short = wakePathKey("PEER_SERVICE", "client.app", "target.app", "target.app/.push.Wake")
        val long = wakePathKey("PEER_SERVICE", "client.app", "target.app", "target.app/target.app.push.Wake")
        val bare = wakePathKey("PEER_SERVICE", "client.app", "target.app", ".push.Wake")
        assertThat(short).isEqualTo("PEER_SERVICE|client.app|target.app|target.app.push.Wake")
        assertThat(long).isEqualTo(short)
        assertThat(bare).isEqualTo(short)
    }

    @Test fun givenActionsAndMissingParts_whenBuildingPathKeys_thenActionsStayAsReported() {
        assertThat(wakePathKey("SYSTEM_BROADCAST", "android", "target.app", "android.intent.action.BOOT_COMPLETED"))
            .isEqualTo("SYSTEM_BROADCAST|android|target.app|android.intent.action.BOOT_COMPLETED")
        assertThat(wakePathKey("UNKNOWN", null, "target.app", null)).isEqualTo("UNKNOWN||target.app|")
        // A component of another package cannot be qualified against the target and is kept.
        assertThat(wakePathKey("PEER_SERVICE", "a", "target.app", "other.app/.Svc")).endsWith("|other.app/.Svc")
    }

    @Test fun givenVariableTagParts_whenNormalizing_thenTheSameLogicalTagRemains() {
        assertThat(normalizeWakeTag("sync-123e4567-e89b-12d3-a456-426614174000")).isEqualTo("sync-#")
        assertThat(normalizeWakeTag("*job*/42/com.app/.Worker_7f3a9c21")).isEqualTo("*job*/#/com.app/.Worker_#")
        assertThat(normalizeWakeTag("retry 3 of 10")).isEqualTo("retry # of #")
        assertThat(normalizeWakeTag("WorkManager.deadbeef")).isEqualTo("WorkManager.deadbeef")
        assertThat(normalizeWakeTag("account@example.com")).isEqualTo("account@example.com")
        assertThat(normalizeWakeTag(normalizeWakeTag("job 1a2b3c4d5e"))).isEqualTo("job #")
    }

    @Test fun givenBothOrNeitherKind_whenCreatingFinding_thenItIsRejected() {
        val revive = WakeFinding.revive("t", ReviveTrigger.ALARM, null, "abc", null, null, Justification.NONE, Confidence.DIRECT, 5)
        assertThat(revive.pathKey).isEqualTo("ALARM||t|abc")
        assertThat(revive.durationNanos).isEqualTo(0)
        assertThrows(IllegalArgumentException::class.java) { revive.copy(hold = HoldCause.WAKELOCK) }
        assertThrows(IllegalArgumentException::class.java) { revive.copy(trigger = null) }
        val hold = WakeFinding.hold("t", HoldCause.UNKNOWN, null, null, Justification.NONE, Confidence.SNAPSHOT, 1, 2)
        // A hold never shares a key with a revive of the same kind name.
        assertThat(hold.pathKey).isEqualTo("HOLD_UNKNOWN||t|")
        assertThat(WakeFinding.revive("t", ReviveTrigger.UNKNOWN, null, null, null, null, Justification.NONE, Confidence.DIRECT, 5).pathKey)
            .isEqualTo("UNKNOWN||t|")
        assertThat(revive.copy(chainRoot = "PEER_SERVICE|a|b|", chainDepth = 2).chainDepth).isEqualTo(2)
        assertThrows(IllegalArgumentException::class.java) { revive.copy(chainRoot = "PEER_SERVICE|a|b|") }
        assertThrows(IllegalArgumentException::class.java) { revive.copy(chainDepth = 1) }
        assertThrows(IllegalArgumentException::class.java) { hold.copy(chainRoot = "PEER_SERVICE|a|b|", chainDepth = 1) }
    }
}
