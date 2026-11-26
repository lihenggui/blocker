// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakeVerdict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveWakeStateTest {
    @Test fun givenMutualPaths_whenTheOwnerComponentsChange_thenBothDirectionsUseTheirOwnTarget() = runTest {
        val f = WakeStateTestFixture(this)
        val incoming = receiver(TEST_APP, "Wake")
        val outgoing = receiver(TEST_PEER, "Wake")
        val inside = finding("in", ReviveTrigger.PEER_BROADCAST, detail = "$TEST_APP/.Wake")
        val outside = finding("out", ReviveTrigger.PEER_BROADCAST, TEST_PEER, TEST_APP, "$TEST_PEER/.Wake")
        f.findings.value = listOf(inside, outside)
        f.status(incoming, off = true)
        f.status(outgoing, off = true, user = UserId(10))
        f.state(TEST_APP).test {
            val initial = awaitItem()
            assertThat(initial.activity.peers.single().direction).isEqualTo(WakeDirection.MUTUAL)
            assertThat(initial.paths.getValue("in").block).isEqualTo(WakePathBlock.USER)
            assertThat(initial.paths.getValue("in").blockedAtMillis).isNull()
            assertThat(initial.paths.getValue("in").seenSinceBlock).isFalse()
            assertThat(initial.paths.getValue("out").block).isEqualTo(WakePathBlock.NONE)
            assertThat(initial.allBlocked).isFalse()
            assertThat(f.observedApps).containsExactly(AppKey(TEST_APP, UserId.SYSTEM), AppKey(TEST_PEER, UserId.SYSTEM))

            f.status(outgoing, off = true)
            assertThat(awaitItem().allBlocked).isTrue()
            f.status(incoming, off = false)
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.NONE)
        }
    }

    @Test fun givenPathsShareAManifestAction_whenComponentStatesChange_thenReceiversAreResolvedOnce() = runTest {
        val f = WakeStateTestFixture(this)
        val action = "example.WAKE"
        val wake = receiver(TEST_APP, "Wake")
        f.receivers[TEST_APP to action] = listOf(wake)
        f.findings.value = listOf(
            finding("first", ReviveTrigger.PEER_BROADCAST, detail = action),
            finding("second", ReviveTrigger.PEER_BROADCAST, actor = TEST_OTHER, detail = action),
        )
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.keys).containsExactly("first", "second")

            f.status(wake, off = true)
            assertThat(awaitItem().allBlocked).isTrue()
            assertThat(f.receiverQueries).containsExactly(TEST_APP to action)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenPathsToTwoTargets_whenAnUnrelatedComponentChanges_thenNeitherPathIsRecomputed() = runTest {
        val f = WakeStateTestFixture(this)
        val incoming = receiver(TEST_APP, "Wake")
        val outgoing = receiver(TEST_PEER, "Wake")
        f.findings.value = listOf(
            finding("in", ReviveTrigger.PEER_BROADCAST, detail = "$TEST_APP/.Wake"),
            finding("out", ReviveTrigger.PEER_BROADCAST, TEST_PEER, TEST_APP, "$TEST_PEER/.Wake"),
        )
        f.status(incoming, off = true)
        f.status(outgoing, off = true)
        val checked = mutableListOf<ComponentId>()
        val owned = receiver(TEST_OTHER, "Owned").id
        f.automatic.ownedComponents.value = object : Set<ComponentId> by setOf(owned) {
            override fun contains(element: ComponentId): Boolean {
                checked += element
                return element == owned
            }
        }
        f.state(TEST_APP).test {
            assertThat(awaitItem().allBlocked).isTrue()
            assertThat(checked).containsExactly(incoming.id, outgoing.id)
            checked.clear()

            f.status(receiver(TEST_APP, "Unrelated"), off = true)
            runCurrent()
            expectNoEvents()
            assertThat(checked).isEmpty()

            f.status(incoming, off = false)
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.NONE)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenBroadcastAction_whenZeroSomeOrAllReceiversAreOff_thenOnlyAllOffWithAReceiverBlocks() = runTest {
        val f = WakeStateTestFixture(this)
        val action = "example.BOOT"
        val first = receiver(TEST_APP, "First")
        val second = receiver(TEST_APP, "Second")
        val path = finding("action", ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = action)
        f.findings.value = listOf(path, finding("alarm", ReviveTrigger.ALARM, actor = null))
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("action").block).isEqualTo(WakePathBlock.NONE)
        }

        f.receivers[TEST_APP to action] = listOf(first, second)
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("action").block).isEqualTo(WakePathBlock.NONE)
            f.status(first, off = true)
            f.status(second, off = false)
            f.manual.blocks.value = listOf(path.toWakePath().blockRow(99))
            runCurrent()
            expectNoEvents()

            f.status(second, off = true)
            val blocked = awaitItem()
            assertThat(blocked.paths.keys).containsExactly("action")
            assertThat(blocked.paths.getValue("action").block).isEqualTo(WakePathBlock.USER)
            assertThat(blocked.paths.getValue("action").seenSinceBlock).isTrue()
            assertThat(blocked.allBlocked).isTrue()

            f.status(first, off = false)
            val partlyOff = awaitItem()
            assertThat(partlyOff.paths.getValue("action").block).isEqualTo(WakePathBlock.NONE)
            assertThat(partlyOff.paths.getValue("action").blockedAtMillis).isNull()
        }
    }

    @Test fun givenReviewedAndUserFirewallBlocks_whenRowsAndEvidenceChange_thenUserBlocksTakePrecedence() = runTest {
        val f = WakeStateTestFixture(this)
        val finding = finding("in")
        val path = finding.toWakePath()
        val automatic = AutomaticWakeBlock("exact", TEST_PEER, TEST_APP, "$TEST_APP/.Wake", "", active = true, allowed = false)
        val other = listOf(
            automatic.copy(pathId = "component", component = "$TEST_APP.Other"),
            automatic.copy(pathId = "target", caller = TEST_APP, target = TEST_PEER),
            automatic.copy(pathId = "caller", caller = TEST_OTHER),
        )
        f.findings.value = listOf(finding)
        f.automatic.blocks.value = listOf(automatic) + other
        f.manual.blocks.value = listOf(path.blockRow(method = WakeBlockMethod.COMPONENT))

        f.state(TEST_APP).test {
            val first = awaitItem()
            assertThat(first.paths.getValue("in").block).isEqualTo(WakePathBlock.AUTOMATIC)
            assertThat(first.paths.getValue("in").blockedAtMillis).isNull()
            assertThat(first.automatic).containsExactlyElementsIn(other)
            assertThat(first.allBlocked).isTrue()

            f.manual.blocks.value = listOf(path.blockRow())
            val blocked = awaitItem().paths.getValue("in")
            assertThat(blocked.block).isEqualTo(WakePathBlock.USER)
            assertThat(blocked.blockedAtMillis).isEqualTo(100)
            assertThat(blocked.seenSinceBlock).isFalse()
            f.findings.value = listOf(finding.copy(lastSeenNanos = 101_000_000))
            assertThat(awaitItem().paths.getValue("in").seenSinceBlock).isTrue()

            f.automatic.blocks.value = listOf(automatic.copy(active = false, allowed = true)) + other
            f.manual.blocks.value = emptyList()
            val allowed = awaitItem()
            assertThat(allowed.paths.getValue("in").block).isEqualTo(WakePathBlock.NONE)
            assertThat(allowed.allBlocked).isFalse()
        }
    }

    @Test fun givenAServicePathWithoutAnAction_whenProtectionHasAnAction_thenTheRuleMatchesThePath() = runTest {
        val f = WakeStateTestFixture(this)
        f.findings.value = listOf(finding("incoming"))
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP/.Wake", "example.BIND", active = true, allowed = false))

        f.state(TEST_APP).test {
            val state = awaitItem()
            assertThat(state.paths.getValue("incoming").block).isEqualTo(WakePathBlock.AUTOMATIC)
            assertThat(state.automatic).isEmpty()
            assertThat(state.allBlocked).isTrue()
        }
    }

    @Test fun givenProtectionOwnsDisabledComponent_whenUserClaimsIt_thenUserBlockTakesPrecedence() = runTest {
        val f = WakeStateTestFixture(this)
        val named = receiver(TEST_APP, "Wake")
        val incoming = finding("in", ReviveTrigger.PEER_BROADCAST, detail = "$TEST_APP/.Wake")
        f.findings.value = listOf(incoming)
        f.status(named, off = true)
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("pm", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false))
        f.automatic.ownedComponents.value = setOf(named.id)
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.AUTOMATIC)
            f.automatic.ownedComponents.value = emptySet()
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.USER)
        }
    }

    @Test fun givenOverviewVerdicts_whenUnmatchedProtectionIsAllowed_thenOnlyNonNormalFullyBlockedAppsAreMarked() = runTest {
        val f = WakeStateTestFixture(this)
        val away = receiver(TEST_PEER, "Wake")
        val normal = receiver(TEST_OTHER, "Wake")
        val extra = AutomaticWakeBlock("extra", TEST_PEER, TEST_APP, "$TEST_APP.Extra", "", active = true, allowed = false)
        f.findings.value = listOf(
            finding("out", ReviveTrigger.PEER_BROADCAST, target = TEST_PEER, actor = TEST_APP, detail = "$TEST_PEER/.Wake"),
            finding("normal", ReviveTrigger.SYSTEM_BROADCAST, target = TEST_OTHER, actor = "android", detail = "$TEST_OTHER/.Wake"),
        )
        f.verdicts.value = listOf(verdict(TEST_APP, WakeVerdict.SUSPICIOUS), verdict(TEST_PEER, WakeVerdict.KEEP_ALIVE), verdict(TEST_OTHER, WakeVerdict.NORMAL))
        f.status(away, off = true)
        f.status(normal, off = true)
        f.automatic.blocks.value = listOf(extra)
        f.overview().test {
            assertThat(awaitItem().associate { it.packageName to it.allBlocked })
                .containsExactly(TEST_APP, true, TEST_PEER, true, TEST_OTHER, false)
            assertThat(f.observedApps).containsExactly(AppKey(TEST_PEER, UserId.SYSTEM))
            f.automatic.blocks.value = listOf(extra.copy(active = false, allowed = true))
            assertThat(awaitItem().associate { it.packageName to it.allBlocked })
                .containsExactly(TEST_APP, false, TEST_PEER, false, TEST_OTHER, false)
        }
    }

    @Test fun givenAllPathsAreBlocked_whenAnUnappliedReviewIsUnmatched_thenTheAppIsStillBlocked() = runTest {
        val f = WakeStateTestFixture(this)
        val incoming = finding("incoming")
        f.findings.value = listOf(incoming)
        f.manual.blocks.value = listOf(incoming.toWakePath().blockRow())
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("unapplied", TEST_PEER, TEST_APP, "$TEST_APP.Other", "", active = false, allowed = false))

        f.state(TEST_APP).test {
            val state = awaitItem()
            assertThat(state.automatic).isEmpty()
            assertThat(state.allBlocked).isTrue()
        }
    }

    @Test fun givenOnlyUnblockableEvidence_whenObserving_thenAnAppWithNoBlockChoicesStaysVisible() = runTest {
        val f = WakeStateTestFixture(this)
        f.findings.value = listOf(finding("alarm", ReviveTrigger.ALARM, actor = null))
        f.state(TEST_APP).test {
            val state = awaitItem()
            assertThat(state.paths).isEmpty()
            assertThat(state.allBlocked).isFalse()
        }
    }
}
