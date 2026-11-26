// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeExemption
import com.merxury.blocker.core.database.wake.WakeRelation
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeAppEnvironment
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakeSpecialAccess
import com.merxury.blocker.core.model.wake.WakeVerdict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ObserveWakeStateTest {
    @Test fun givenPowerSnapshots_whenObservingAnAppWithoutFindings_thenEnvironmentUpdatesAndClears() = runTest {
        val f = WakeStateTestFixture(this)
        val app = WakeExemption(TEST_APP, "system-excidle,user", 40, null, 10)
        f.exemptions.value = listOf(app, app.copy(target = TEST_PEER, idleWhitelist = "system", standbyBucket = 50))

        f.state(TEST_APP).test {
            val initial = awaitItem()
            assertThat(initial.paths).isEmpty()
            assertThat(initial.environment).isEqualTo(WakeAppEnvironment(userIdleAllowlisted = true, systemIdleAllowlisted = true, standbyBucket = 40))
            assertThat(f.observedEnvironmentTargets).containsExactly(listOf(TEST_APP), listOf(TEST_APP))

            f.exemptions.value = listOf(app.copy(idleWhitelist = "system", standbyBucket = 20))
            assertThat(awaitItem().environment).isEqualTo(WakeAppEnvironment(systemIdleAllowlisted = true, standbyBucket = 20))
            f.exemptions.value = listOf(app.copy(idleWhitelist = null, standbyBucket = null))
            assertThat(awaitItem().environment).isEqualTo(WakeAppEnvironment())
        }
    }

    @Test fun givenGrantsInHistory_whenLatestSnapshotsChange_thenRevokedAccessDisappears() = runTest {
        val f = WakeStateTestFixture(this)
        val rows = listOf(
            wakeRecord("a11y", "SystemBinding", 10, targetPackage = TEST_APP, action = "ACCESSIBILITY"),
            wakeRecord("listener", "SystemBinding", 10, targetPackage = TEST_APP, action = "LISTENERS"),
            wakeRecord("vpn", "SystemBinding", 10, targetPackage = TEST_APP, action = "VPN"),
            wakeRecord("ime", "SystemBinding", 10, targetPackage = TEST_APP, action = "INPUT"),
            wakeRecord("admin", "DeviceAdmin", 10, targetPackage = TEST_APP),
            wakeRecord("companion", "CompanionAssociation", 10, targetPackage = TEST_APP),
            wakeRecord("wallpaper", "WallpaperBinding", 10, targetPackage = TEST_APP),
            wakeRecord("widget", "WidgetProvider", 10, targetPackage = TEST_APP),
            wakeRecord("sync", "SyncAdapter", 10, targetPackage = TEST_APP),
        )
        f.bindings.value = rows

        f.state(TEST_APP).test {
            assertThat(awaitItem().environment.specialAccess).containsExactlyElementsIn(WakeSpecialAccess.entries)
            val newer = rows.first().wallMillis + 1
            f.bindings.value = rows + listOf(
                rows[0].copy(id = "other-a11y", targetPackage = TEST_PEER, wallMillis = newer),
                rows[1].copy(id = "empty-listeners", targetPackage = null, wallMillis = newer),
                rows[4].copy(id = "empty-admins", targetPackage = null, wallMillis = newer),
            )
            assertThat(awaitItem().environment.specialAccess).containsExactly(
                WakeSpecialAccess.ALWAYS_ON_VPN,
                WakeSpecialAccess.INPUT_METHOD,
                WakeSpecialAccess.COMPANION_DEVICE,
                WakeSpecialAccess.LIVE_WALLPAPER,
                WakeSpecialAccess.WIDGET,
                WakeSpecialAccess.SYNC_ADAPTER,
            )
        }
    }

    @Test fun givenAWeekOldBlock_whenOpeningEitherApp_thenTheLatestStoredFindingCanBeUnblocked() = runTest {
        val f = WakeStateTestFixture(this)
        val key = "PEER_SERVICE|$TEST_PEER|$TEST_APP|$TEST_APP.Wake"
        val latest = finding(key, seen = 90).let { it.copy(day = it.day - 8) }
        val older = latest.copy(id = "older", day = latest.day - 1, lastSeenNanos = 0)
        f.findings.value = listOf(older, latest)
        val block = latest.toWakePath().blockRow(at = 100)
        f.manual.blocks.value = listOf(block)

        for (app in listOf(TEST_APP, TEST_PEER)) {
            val state = f.state(app).first()
            val link = state.activity.peers.single()
            assertThat(link.direction).isEqualTo(if (app == TEST_APP) WakeDirection.INBOUND else WakeDirection.OUTBOUND)
            val path = state.paths.getValue(key)
            assertThat(path.block).isEqualTo(WakePathBlock.USER)
            assertThat(path.seenSinceBlock).isFalse()
            assertThat(path.path.lastSeenMillis).isEqualTo(latest.toWakePath().lastSeenMillis)
            assertThat(path.path.count).isEqualTo(0)
        }

        val path = f.state(TEST_PEER).first().paths.getValue(key)
        val result = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)(listOf(path), blocked = false)
        assertThat(result.done).containsExactly(key)
        assertThat(f.state(TEST_APP).first().paths).isEmpty()
        assertThat(f.state(TEST_PEER).first().paths).isEmpty()
    }

    @Test fun givenAStoredBlockWithNoFinding_whenOpeningTheSender_thenTheStoredDetailsStillControlThePath() = runTest {
        val f = WakeStateTestFixture(this)
        val block = finding("PEER_SERVICE|$TEST_PEER|$TEST_APP|$TEST_APP.Wake").toWakePath().blockRow()
        f.manual.blocks.value = listOf(block)

        val state = f.state(TEST_PEER).first()

        val outbound = state.activity.peers.single().outbound.single()
        assertThat(outbound.component).isEqualTo(block.component)
        assertThat(state.paths.getValue(block.pathKey).block).isEqualTo(WakePathBlock.USER)
    }

    @Test fun givenOwnBroadcastToRemoteProcess_whenBuildingThePath_thenItCanDisableTheReceiver() = runTest {
        val f = WakeStateTestFixture(this)
        val receiver = receiver(TEST_APP, "Wake")
        f.status(receiver, off = false)
        val uid = 10022
        val pid = 42
        val start = wakeRecord(
            "start",
            "am_proc_start",
            2_000_000_000,
            source = SYSTEM_WAKE_EVENTS,
            targetUid = uid,
            targetPackage = TEST_APP,
            process = "$TEST_APP:remote",
            component = "$TEST_APP/.Wake",
            pid = pid,
        )
        val stats = start.copy(id = "stats", kind = "ProcessStartTime", source = "statsd", startType = 3)
        val broadcast = wakeRecord(
            "broadcast", "BroadcastDeliveryEventReported", 1_000_000_000, callerUid = uid, callerPackage = TEST_APP,
            targetUid = uid, targetPackage = TEST_APP, action = "$TEST_APP.WAKE", attributes = mapOf("sender_proc_state" to "1004"),
        )
        val evidence = WakeDetectionEvidence(
            listOf(broadcast, start, stats),
            listOf(WakeRelation(broadcast.id, stats.id, "MATCHING_CALLER")),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            mapOf(uid to setOf(TEST_APP)),
        )
        f.findings.value = WakeDetection.daily(WakeDetection.detect(evidence), ZoneOffset.UTC)
            .map { it.copy(day = LocalDate.now().toEpochDay()) }

        val path = f.state(TEST_APP).first().paths.values.single()

        assertThat(path.path.trigger).isEqualTo(ReviveTrigger.SELF_PROCESS)
        assertThat(path.path.componentType).isEqualTo(ComponentType.RECEIVER)
        assertThat(path.path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        val result = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)(listOf(path), blocked = true)
        assertThat(result.done).containsExactly(path.path.pathKey)
        assertThat(f.manual.requests).containsExactly(path.path to WakeBlockMethod.COMPONENT)
    }

    @Test fun givenMutualPaths_whenTheOwnerComponentsChange_thenBothDirectionsUseTheirOwnTarget() = runTest {
        val f = WakeStateTestFixture(this)
        val incoming = receiver(TEST_APP, "Wake")
        val outgoing = receiver(TEST_PEER, "Wake")
        val action = "example.WAKE"
        val inside = finding("in", ReviveTrigger.PEER_BROADCAST, detail = action)
        val outside = finding("out", ReviveTrigger.PEER_BROADCAST, TEST_PEER, TEST_APP, action)
        f.findings.value = listOf(inside, outside)
        f.receivers[TEST_APP to action] = listOf(incoming)
        f.receivers[TEST_PEER to action] = listOf(outgoing)
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
            assertThat(f.observedBackgroundApps).isEmpty()

            f.status(outgoing, off = true)
            assertThat(awaitItem().allBlocked).isTrue()
            f.status(incoming, off = false)
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.NONE)
        }
    }

    @Test fun givenReceiverBlockedOnlyInIfw_whenObservingBroadcastPaths_thenOnlyThePmBlockCutsThePath() = runTest {
        val f = WakeStateTestFixture(this)
        val receiver = receiver(TEST_APP, "Wake")
        f.findings.value = listOf(finding("broadcast", ReviveTrigger.PEER_BROADCAST, detail = "$TEST_APP/.Wake"))
        f.status(receiver, off = false, ifw = IfwBlock.BLOCKED)

        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("broadcast").block).isEqualTo(WakePathBlock.NONE)
            f.status(receiver, off = true, ifw = IfwBlock.BLOCKED)
            assertThat(awaitItem().paths.getValue("broadcast").block).isEqualTo(WakePathBlock.USER)
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
        val action = "example.WAKE"
        f.findings.value = listOf(
            finding("in", ReviveTrigger.PEER_BROADCAST, detail = action),
            finding("out", ReviveTrigger.PEER_BROADCAST, TEST_PEER, TEST_APP, action),
        )
        f.receivers[TEST_APP to action] = listOf(incoming)
        f.receivers[TEST_PEER to action] = listOf(outgoing)
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

    @Test
    fun givenBroadcastAction_whenZeroSomeOrAllReceiversAreOff_thenOnlyAllOffWithAReceiverBlocks() = runTest {
        val f = WakeStateTestFixture(this)
        val action = "example.BOOT"
        val first = receiver(TEST_APP, "First")
        val second = receiver(TEST_APP, "Second")
        val path = finding("action", ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = action)
        f.findings.value = listOf(path, finding("unnamed-restart", ReviveTrigger.STICKY_RESTART, actor = null, detail = null))
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("action").block).isEqualTo(WakePathBlock.NONE)
        }

        f.receivers[TEST_APP to action] = listOf(first, second)
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("action").block).isEqualTo(WakePathBlock.NONE)
            f.status(first, off = true)
            f.status(second, off = false)
            f.manual.blocks.value = listOf(path.toWakePath().blockRow(99))
            assertThat(awaitItem().paths.getValue("action").reEnabledByApp).isTrue()

            f.status(second, off = true)
            val blocked = awaitItem()
            assertThat(blocked.paths.keys).containsExactly("action")
            assertThat(blocked.paths.getValue("action").block).isEqualTo(WakePathBlock.USER)
            assertThat(blocked.paths.getValue("action").seenSinceBlock).isTrue()
            assertThat(blocked.paths.getValue("action").reEnabledByApp).isFalse()
            assertThat(blocked.allBlocked).isFalse()

            f.status(first, off = false)
            val partlyOff = awaitItem()
            assertThat(partlyOff.paths.getValue("action").block).isEqualTo(WakePathBlock.NONE)
            assertThat(partlyOff.paths.getValue("action").blockedAtMillis).isNull()
            assertThat(partlyOff.paths.getValue("action").reEnabledByApp).isTrue()
            f.manual.blocks.value = emptyList()
            assertThat(awaitItem().paths.getValue("action").reEnabledByApp).isFalse()
        }
    }

    @Test fun givenMixedBlocks_whenBackgroundUsageChanges_thenDetailAndOverviewUpdateTogether() = runTest {
        val f = WakeStateTestFixture(this)
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        val incoming = finding("incoming")
        val alarm = finding("alarm", ReviveTrigger.ALARM, actor = null, detail = null)
        val wakelock = alarm.copy(pathKey = "wakelock", trigger = null, hold = HoldCause.WAKELOCK.name, screenOffNanos = 100)
        val broadcast = finding("broadcast", ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "$TEST_APP/.Receiver")
        val automatic = AutomaticWakeBlock("known", TEST_PEER, TEST_APP, "$TEST_APP.Known", "", active = true, allowed = false)
        f.findings.value = listOf(incoming, alarm, wakelock, broadcast)
        f.verdicts.value = listOf(verdict(TEST_APP, WakeVerdict.KEEP_ALIVE))
        f.manual.blocks.value = listOf(incoming.toWakePath().blockRow(), alarm.toWakePath().blockRow(at = 1))
        f.status(receiver(TEST_APP, "Receiver"), off = true)
        f.automatic.blocks.value = listOf(automatic)

        f.overview().test overview@{
            assertThat(awaitItem().single().allBlocked).isFalse()
            assertThat(f.observedBackgroundApps).containsExactly(app)
            assertThat(f.observedEnvironmentTargets).isEmpty()
            f.state(TEST_APP).test {
                val allowed = awaitItem()
                assertThat(allowed.paths.keys).containsExactly("incoming", "alarm", "wakelock", "broadcast")
                assertThat(allowed.paths.getValue("alarm").block).isEqualTo(WakePathBlock.NONE)
                assertThat(allowed.paths.getValue("wakelock").block).isEqualTo(WakePathBlock.NONE)
                assertThat(allowed.allBlocked).isFalse()

                f.background.setAllowed(app, false)
                val restricted = awaitItem()
                assertThat(restricted.allBlocked).isTrue()
                assertThat(restricted.paths.getValue("alarm").block).isEqualTo(WakePathBlock.USER)
                assertThat(restricted.paths.getValue("alarm").blockedAtMillis).isNull()
                assertThat(restricted.paths.getValue("alarm").seenSinceBlock).isFalse()
                assertThat(restricted.paths.getValue("wakelock").block).isEqualTo(WakePathBlock.USER)
                assertThat(this@overview.awaitItem().single().allBlocked).isTrue()

                f.background.sendUsage(app, null)
                assertThat(awaitItem().allBlocked).isFalse()
                assertThat(this@overview.awaitItem().single().allBlocked).isFalse()
            }
        }
    }

    @Test fun givenOnlyBackgroundPaths_whenAllowingTheApp_thenKeepsPathsVisibleAndClearsAllBlocked() = runTest {
        val f = WakeStateTestFixture(this)
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        f.findings.value = listOf(finding("job", ReviveTrigger.JOB, actor = null, detail = null))
        f.background.sendUsage(app, BackgroundUsage(allowed = false, changeable = true))

        f.state(TEST_APP).test {
            val restricted = awaitItem()
            assertThat(restricted.paths.getValue("job").block).isEqualTo(WakePathBlock.USER)
            assertThat(restricted.allBlocked).isTrue()

            f.background.setAllowed(app, true)
            val allowed = awaitItem()
            assertThat(allowed.paths.getValue("job").block).isEqualTo(WakePathBlock.NONE)
            assertThat(allowed.allBlocked).isFalse()
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
        f.manual.blocks.value = listOf(incoming.toWakePath().blockRow(method = WakeBlockMethod.COMPONENT))
        f.status(named, off = true)
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("pm", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false))
        f.automatic.ownedComponents.value = setOf(named.id)
        f.state(TEST_APP).test {
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.AUTOMATIC)
            f.automatic.ownedComponents.value = emptySet()
            assertThat(awaitItem().paths.getValue("in").block).isEqualTo(WakePathBlock.USER)
        }
    }

    @Test fun givenDifferentSavedBlocks_whenTheirReceiverIsObserved_thenOnlyTheStaticAliasIsClaimed() = runTest {
        val f = WakeStateTestFixture(this)
        val receiver = receiver(TEST_APP, "Wake")
        val live = finding("observed", ReviveTrigger.SYSTEM_BROADCAST, actor = "android", detail = "$TEST_APP/.Wake")
        val saved = live.toWakePath().copy(pathKey = "STATIC|BOOT_AUTOSTART|$TEST_APP|$TEST_APP.Wake").blockRow(at = 17)
        val unrelated = live.toWakePath().copy(pathKey = "other-receiver-path").blockRow(at = 23)
        f.findings.value = listOf(live)
        f.manual.blocks.value = listOf(unrelated)
        f.status(receiver, off = true)

        f.state(TEST_APP).test {
            val unclaimed = awaitItem().paths.getValue("observed")
            assertThat(unclaimed.blockedAtMillis).isNull()

            f.manual.blocks.value = listOf(unrelated, saved)
            val blocked = awaitItem().paths.getValue("observed")
            assertThat(blocked.block).isEqualTo(WakePathBlock.USER)
            assertThat(blocked.blockedAtMillis).isEqualTo(17L)
            f.status(receiver, off = false)
            assertThat(awaitItem().paths.getValue("observed").reEnabledByApp).isTrue()

            f.manual.unblock(listOf(saved.pathKey))
            assertThat(awaitItem().paths.getValue("observed").reEnabledByApp).isFalse()
            assertThat(f.manual.blocks.value).containsExactly(unrelated)
        }
    }

    @Test fun givenAnExistingPeerActivityPmBlock_whenTheAppReEnablesIt_thenTheBlockCanBeReappliedOrRemoved() = runTest {
        val f = WakeStateTestFixture(this)
        val wake = receiver(TEST_APP, "Wake").copy(type = com.merxury.blocker.core.model.ComponentType.ACTIVITY)
        val incoming = finding("peer", ReviveTrigger.PEER_ACTIVITY, detail = "$TEST_APP/.Wake")
        f.findings.value = listOf(incoming)
        f.manual.blocks.value = listOf(incoming.toWakePath().blockRow(method = WakeBlockMethod.COMPONENT))
        f.status(wake, off = true)

        f.state(TEST_APP).test {
            val blocked = awaitItem().paths.getValue("peer")
            assertThat(blocked.path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
            assertThat(blocked.block).isEqualTo(WakePathBlock.USER)
            assertThat(blocked.reEnabledByApp).isFalse()

            f.status(wake, off = false)
            val enabled = awaitItem().paths.getValue("peer")
            assertThat(enabled.path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
            assertThat(enabled.block).isEqualTo(WakePathBlock.NONE)
            assertThat(enabled.reEnabledByApp).isTrue()

            f.status(wake, off = true)
            assertThat(awaitItem().paths.getValue("peer").reEnabledByApp).isFalse()
            f.status(wake, off = false)
            assertThat(awaitItem().paths.getValue("peer").reEnabledByApp).isTrue()
            f.manual.blocks.value = emptyList()
            val removed = awaitItem().paths.getValue("peer")
            assertThat(removed.path.blockMethod).isEqualTo(WakeBlockMethod.FIREWALL_RULE)
            assertThat(removed.block).isEqualTo(WakePathBlock.NONE)
            assertThat(removed.reEnabledByApp).isFalse()
        }
    }

    @Test fun givenOverviewVerdicts_whenUnmatchedProtectionIsAllowed_thenOnlyNonNormalFullyBlockedAppsAreMarked() = runTest {
        val f = WakeStateTestFixture(this)
        val away = receiver(TEST_PEER, "Wake")
        val normal = receiver(TEST_OTHER, "Wake")
        val extra = AutomaticWakeBlock("extra", TEST_PEER, TEST_APP, "$TEST_APP.Extra", "", active = true, allowed = false)
        val outgoing = finding("out", ReviveTrigger.PEER_BROADCAST, target = TEST_PEER, actor = TEST_APP, detail = "$TEST_PEER/.Wake")
        f.findings.value = listOf(
            outgoing,
            finding("normal", ReviveTrigger.SYSTEM_BROADCAST, target = TEST_OTHER, actor = "android", detail = "$TEST_OTHER/.Wake"),
        )
        f.manual.blocks.value = listOf(outgoing.toWakePath().blockRow(method = WakeBlockMethod.COMPONENT))
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

    @Test fun givenBlockedAndUnblockablePaths_whenObserving_thenTheAppIsNotFullyBlocked() = runTest {
        val f = WakeStateTestFixture(this)
        val incoming = finding("incoming")
        val uncontrollable = finding("unnamed-restart", ReviveTrigger.STICKY_RESTART, actor = null, detail = null)
        assertThat(uncontrollable.toWakePath().blockMethod).isEqualTo(WakeBlockMethod.NONE)
        f.findings.value = listOf(incoming, uncontrollable)
        f.manual.blocks.value = listOf(incoming.toWakePath().blockRow())

        f.state(TEST_APP).test {
            val state = awaitItem()
            assertThat(state.paths.getValue("incoming").block).isEqualTo(WakePathBlock.USER)
            assertThat(state.allBlocked).isFalse()
        }
    }

    @Test fun givenOnlyUnblockableEvidence_whenObserving_thenAnAppWithNoBlockChoicesStaysVisible() = runTest {
        val f = WakeStateTestFixture(this)
        f.findings.value = listOf(finding("unnamed-restart", ReviveTrigger.STICKY_RESTART, actor = null, detail = null))
        f.state(TEST_APP).test {
            val state = awaitItem()
            assertThat(state.paths).isEmpty()
            assertThat(state.allBlocked).isFalse()
        }
    }
}
