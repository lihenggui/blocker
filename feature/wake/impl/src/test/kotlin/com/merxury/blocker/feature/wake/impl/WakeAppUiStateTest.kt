// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.model.wake.WakePeerLink
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import org.junit.Test
import com.merxury.blocker.feature.appdetail.api.R.string as appString

class WakeAppUiStateTest {
    @Test
    fun givenUnsortedRelations_whenMapping_thenGroupsByDirectionAndSortsEachByCount() {
        val incoming = detailPath("in", actor = "peer", count = 3, last = 20)
        val outgoing = detailPath("out", target = "peer", count = 2, last = 30)
        val other = detailPath("other", actor = "missing", count = 4)
        val state = detailState(
            peers = listOf(
                WakePeerLink("outbound", WakeDirection.OUTBOUND, emptyList(), listOf(outgoing.copy(pathKey = "outbound", target = "outbound"))),
                WakePeerLink("few", WakeDirection.INBOUND, listOf(other.copy(pathKey = "few", actor = "few", count = 1)), emptyList()),
                WakePeerLink("missing", WakeDirection.INBOUND, listOf(other), emptyList()),
                WakePeerLink("peer", WakeDirection.MUTUAL, listOf(incoming), listOf(outgoing)),
            ),
            automatic = listOf(AutomaticWakeBlock("known", "caller", DETAIL_PACKAGE, "$DETAIL_PACKAGE.Known", "bind", true, false)),
        )
        val ui = buildWakeAppState(state, 0, listOf(wakeTestApp("peer").copy(label = "Peer")), emptyMap())
        assertThat(ui.relations.map { it.key }).containsExactly("peer:peer", "peer:missing", "peer:few", "peer:outbound", "automatic:known").inOrder()
        assertThat(ui.relations.map { it.title }).containsExactly(
            WakeText.Resource(string.feature_wake_api_relation_mutual, "Peer"),
            WakeText.Resource(string.feature_wake_api_relation_inbound, "missing"),
            WakeText.Resource(string.feature_wake_api_relation_inbound, "few"),
            WakeText.Resource(string.feature_wake_api_relation_outbound, "outbound"),
            WakeText.Resource(string.feature_wake_api_method_known),
        ).inOrder()
        assertThat(ui.relations.first().meta).isEqualTo(WakeText.Latest(5, 30))
        assertThat(ui.relations[1].peer?.icon).isNull()
        assertThat(ui.relations[4].meta).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked_automatically))
    }

    @Test
    fun givenOwnMethods_whenMapping_thenPreservesOrderAndUsesBootAndDurationTexts() {
        val methods = listOf(
            detailMethod(OwnWakeKind.TIMED, detailPath("timer", method = WakeBlockMethod.NONE)),
            detailMethod(OwnWakeKind.SYSTEM_EVENT, detailPath("boot", action = Intent.ACTION_BOOT_COMPLETED)),
            detailMethod(OwnWakeKind.SYSTEM_EVENT, detailPath("locked", action = Intent.ACTION_LOCKED_BOOT_COMPLETED)),
            detailMethod(OwnWakeKind.SYSTEM_EVENT, detailPath("system", action = Intent.ACTION_SCREEN_ON)),
            detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, detailPath("restart")),
            detailMethod(OwnWakeKind.SELF_START, detailPath("self")),
            detailMethod(OwnWakeKind.SCREEN_OFF_RUN, detailPath("screen", screenOff = 300), detailPath("screen2", screenOff = 200)),
        )
        val ui = buildWakeAppState(detailState(own = methods), null, emptyList(), emptyMap())
        assertThat(ui.methods.map { (it.title as WakeText.Resource).id }).containsExactly(
            string.feature_wake_api_method_timed,
            string.feature_wake_api_reason_boot,
            string.feature_wake_api_reason_boot,
            string.feature_wake_api_method_system_event,
            string.feature_wake_api_method_restart,
            string.feature_wake_api_method_self_start,
            string.feature_wake_api_method_screen_off,
        ).inOrder()
        assertThat(ui.methods.first().canToggle).isFalse()
        assertThat(ui.methods.last().meta).isEqualTo(WakeText.Duration(500))
        assertThat(ui.hasRestart).isTrue()
    }

    @Test
    fun givenUserAndAutomaticBlocks_whenCheckingRecency_thenRequiresSuccessfulCheckAfterBlockAndNoNewFinding() {
        val path = detailPath("restart")
        fun meta(check: Long?, block: WakePathBlock, seen: Boolean): WakeText? {
            val state = detailState(
                own = listOf(detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, path)),
                blocks = mapOf(path.pathKey to WakePathState(path, block, 100, seen)),
            )
            return buildWakeAppState(state, check, emptyList(), emptyMap()).methods.single().meta
        }
        assertThat(meta(101, WakePathBlock.USER, false)).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked_not_seen))
        assertThat(meta(100, WakePathBlock.USER, false)).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked))
        assertThat(meta(null, WakePathBlock.USER, false)).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked))
        assertThat(meta(101, WakePathBlock.USER, true)).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked))
        assertThat(meta(101, WakePathBlock.AUTOMATIC, false)).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked_automatically))
    }

    @Test
    fun givenMutualLinkWithCatalogDetails_whenMappingFacts_thenUsesFirstComponentAndBothCallers() {
        val path = detailPath("out", target = "peer", action = "android.intent.action.USER_PRESENT")
        val state = detailState(
            peers = listOf(WakePeerLink("peer", WakeDirection.MUTUAL, listOf(detailPath("in", actor = "peer", component = null)), listOf(path))),
        )
        val app = wakeTestApp(DETAIL_PACKAGE).copy(label = "Calendar")
        val peer = wakeTestApp("peer").copy(label = "Contacts")
        val detail = ComponentDetail("peer.Service", sdkName = "Catalog SDK", disableEffect = "Stops syncing")
        val ui = buildWakeAppState(state, null, listOf(app, peer), mapOf(detail.name to detail))
        assertThat(state.visibleComponentNames()).containsExactly(detail.name)
        assertThat(ui.relations.single().facts).containsExactly(
            WakeFact(
                string.feature_wake_api_fact_component,
                WakeText.Resource(
                    string.feature_wake_api_fact_component_of_app,
                    "Service",
                    "Contacts",
                    WakeText.Resource(string.feature_wake_api_type_service),
                ),
            ),
            WakeFact(string.feature_wake_api_fact_caller, WakeText.Callers(listOf("Contacts", "Calendar"))),
            WakeFact(string.feature_wake_api_fact_action, WakeText.Literal("USER_PRESENT")),
            WakeFact(appString.feature_appdetail_api_belonging_sdk, WakeText.Literal("Catalog SDK")),
            WakeFact(appString.feature_appdetail_api_blocking_effect, WakeText.Literal("Stops syncing")),
        ).inOrder()
    }

    @Test
    fun givenFourInboundPeersAndOneMutual_whenMappingChain_thenShowsTwoPeersAndTheRemainder() {
        val paths = (1..4).map { detailPath("in$it", actor = "peer$it", count = it.toLong()) }
        val peers = paths.mapIndexed { i, path ->
            if (i == 0) {
                WakePeerLink("peer1", WakeDirection.MUTUAL, listOf(path), listOf(detailPath("out", target = "peer1")))
            } else {
                WakePeerLink(requireNotNull(path.actor), WakeDirection.INBOUND, listOf(path), emptyList())
            }
        }
        val state = detailState(peers)
        val ui = buildWakeAppState(state, null, emptyList(), emptyMap())
        assertThat(ui.inbound.visiblePeers.map { it.app.packageName }).containsExactly("peer1", "peer4").inOrder()
        assertThat(ui.inbound.more).isEqualTo(2)
        assertThat(ui.outbound.visiblePeers.map { it.app.packageName }).containsExactly("peer1")
        assertThat(ui.outbound.more).isEqualTo(0)
        assertThat(ui.outbound.cut).isFalse()
        val blocked = state.copy(paths = state.paths.mapValues { it.value.copy(block = WakePathBlock.USER) })
        assertThat(buildWakeAppState(blocked, null, emptyList(), emptyMap()).inbound.cut).isTrue()
        assertThat(WakeChainSide(ui.inbound.peers.take(3)).visiblePeers).hasSize(3)
    }

    @Test
    fun givenMixedRowsAndBackgroundUsage_whenBuildingChoices_thenIncludesOnlyAllowedBlockableRows() {
        val allowed = detailPath("allowed")
        val blocked = detailPath("blocked")
        val none = detailPath("none", method = WakeBlockMethod.NONE)
        val ui = buildWakeAppState(
            detailState(
                own = listOf(
                    detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, allowed),
                    detailMethod(OwnWakeKind.SELF_START, blocked),
                    detailMethod(OwnWakeKind.TIMED, none),
                ),
                blocks = mapOf("blocked" to WakePathState(blocked, WakePathBlock.USER, 100, false)),
            ),
            null,
            emptyList(),
            emptyMap(),
        )
        assertThat(ui.choices.map { it.key }).containsExactly(ui.methods.first().key)
        assertThat(ui.copy(background = BackgroundUsage(true, false)).choices).isEqualTo(ui.choices)
        assertThat(ui.copy(background = BackgroundUsage(true, true)).choices.map { it.key }).containsExactly(ui.methods.first().key, BACKGROUND_CHOICE).inOrder()
        assertThat(ui.normal.parts()).containsExactly(
            WakeText.Count(plurals.feature_wake_api_normal_opened, 2L),
            WakeText.Count(plurals.feature_wake_api_normal_push, 8L),
        ).inOrder()
    }

    @Test
    fun givenVerdictsAndAllBlockedFlag_whenPickingStatus_thenBlockStatusTakesPrecedence() {
        assertThat(wakeStatus(true, WakeVerdict.KEEP_ALIVE)).isEqualTo(WakeAppStatus.AllBlocked)
        assertThat(wakeStatus(false, WakeVerdict.KEEP_ALIVE)).isEqualTo(WakeAppStatus.KeepAlive)
        assertThat(wakeStatus(false, WakeVerdict.SUSPICIOUS)).isEqualTo(WakeAppStatus.Attention)
        assertThat(wakeStatus(false, WakeVerdict.NORMAL)).isEqualTo(WakeAppStatus.None)
    }

    @Test
    fun givenStatus_whenPickingHalo_thenOnlyBlockedHaloRotatesAndNoneUsesAttentionOutline() {
        assertThat(WakeAppStatus.KeepAlive.halo).isEqualTo(WakeHalo.KeepAlive)
        assertThat(WakeAppStatus.Attention.halo).isEqualTo(WakeHalo.Attention)
        assertThat(WakeAppStatus.None.halo).isEqualTo(WakeHalo.Attention)
        assertThat(WakeAppStatus.AllBlocked.halo).isEqualTo(WakeHalo.Blocked)
        assertThat(WakeAppStatus.AllBlocked.halo.rotation).isEqualTo(60f)
    }
}
