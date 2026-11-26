// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeAppEnvironment
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.model.wake.WakePeerLink
import com.merxury.blocker.core.model.wake.WakeSpecialAccess
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
        assertThat(ui.methods.first().canToggle(ui.background)).isFalse()
        assertThat(ui.methods.last().meta).isEqualTo(WakeText.Duration(500))
        assertThat(ui.hasRestart).isTrue()
    }

    @Test
    fun givenSilentPushPaths_whenMappingSystemEvents_thenUsesSilentPushTitleOnlyForPushOnlyRows() {
        val push = detailPath("push", method = WakeBlockMethod.COMPONENT).copy(trigger = ReviveTrigger.PUSH_TRANSPORT)
        val broadcast = detailPath("broadcast", action = Intent.ACTION_SCREEN_ON).copy(trigger = ReviveTrigger.SYSTEM_BROADCAST)
        val methods = listOf(
            detailMethod(OwnWakeKind.SYSTEM_EVENT, push),
            detailMethod(OwnWakeKind.SYSTEM_EVENT, broadcast, push),
        )

        val ui = buildWakeAppState(detailState(own = methods), null, emptyList(), emptyMap())

        assertThat(ui.methods.map { it.title }).containsExactly(
            WakeText.Resource(string.feature_wake_api_method_silent_push),
            WakeText.Resource(string.feature_wake_api_method_system_event),
        ).inOrder()
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
    fun givenComponentReenabledByApp_whenMappingUnblockedRow_thenWarningReplacesActivityMeta() {
        val service = detailPath("service", method = WakeBlockMethod.COMPONENT)
        val blocked = detailPath("blocked", method = WakeBlockMethod.COMPONENT)
        val state = detailState(
            own = listOf(detailMethod(OwnWakeKind.SELF_START, service, blocked)),
            blocks = mapOf(
                service.pathKey to WakePathState(service, WakePathBlock.NONE, null, false, reEnabledByApp = true),
                blocked.pathKey to WakePathState(blocked, WakePathBlock.USER, 100, false),
            ),
        )

        val row = buildWakeAppState(state, null, emptyList(), emptyMap()).methods.single()
        assertThat(row.blocked).isFalse()
        assertThat(row.reEnabledByApp).isTrue()
        assertThat(row.meta).isEqualTo(WakeText.Resource(string.feature_wake_api_re_enabled_by_app))

        val reblocked = state.copy(paths = state.paths.mapValues { (_, path) -> path.copy(block = WakePathBlock.USER) })
        val blockedRow = buildWakeAppState(reblocked, null, emptyList(), emptyMap()).methods.single()
        assertThat(blockedRow.blocked).isTrue()
        assertThat(blockedRow.reEnabledByApp).isFalse()
        assertThat(blockedRow.meta).isEqualTo(WakeText.Resource(string.feature_wake_api_blocked))
    }

    @Test
    fun givenPotentialEntries_whenMapping_thenShowsCategoryStatusAndComponentFacts() {
        val entries = listOf(
            potentialEntry(),
            potentialEntry(PotentialWakeCategory.PUSH_RECEIVER, "PushReceiver", action = "example.PUSH", reEnabledByApp = true),
            potentialEntry(PotentialWakeCategory.SCHEDULED_JOB, "ScheduledJob", ComponentType.SERVICE, action = null, block = WakePathBlock.USER),
            potentialEntry(PotentialWakeCategory.ACCOUNT_SYNC, "SyncService", ComponentType.SERVICE, action = "android.content.SyncAdapter"),
            potentialEntry(PotentialWakeCategory.SYSTEM_BROADCAST, "SystemReceiver", action = Intent.ACTION_SCREEN_ON),
        )
        val catalog = ComponentDetail("$DETAIL_PACKAGE.PushReceiver", sdkName = "Push SDK", disableEffect = "Stops push delivery")
        val environment = WakeAppEnvironment(userIdleAllowlisted = true, specialAccess = setOf(WakeSpecialAccess.SYNC_ADAPTER))
        val ui = buildWakeAppState(detailState().copy(environment = environment), null, emptyList(), mapOf(catalog.name to catalog), entries)

        assertThat(ui.environment).isEqualTo(environment)
        assertThat(ui.potential.map { it.key }).containsExactlyElementsIn(entries.map { "potential:${it.state.path.pathKey}" }).inOrder()
        assertThat(ui.potential.map { (it.title as WakeText.Resource).id }).containsExactly(
            string.feature_wake_api_potential_boot,
            string.feature_wake_api_potential_push,
            string.feature_wake_api_potential_job,
            string.feature_wake_api_potential_sync,
            string.feature_wake_api_potential_system_broadcast,
        ).inOrder()
        assertThat(ui.potential.map { it.meta }).containsExactly(
            WakeText.Literal("BootReceiver"),
            WakeText.Resource(string.feature_wake_api_re_enabled_by_app),
            WakeText.Resource(string.feature_wake_api_blocked),
            WakeText.Literal("SyncService"),
            WakeText.Literal("SystemReceiver"),
        ).inOrder()
        assertThat(ui.potential.map { it.reEnabledByApp }).containsExactly(false, true, false, false, false).inOrder()
        val actions = listOf("BOOT_COMPLETED", "example.PUSH", null, "android.content.SyncAdapter", "SCREEN_ON")
        val types = listOf(
            string.feature_wake_api_type_receiver,
            string.feature_wake_api_type_receiver,
            string.feature_wake_api_type_service,
            string.feature_wake_api_type_service,
            string.feature_wake_api_type_receiver,
        )
        ui.potential.forEachIndexed { index, row ->
            val entry = entries[index]
            val facts = buildList {
                add(
                    WakeFact(
                        string.feature_wake_api_fact_component,
                        WakeText.Resource(
                            string.feature_wake_api_fact_component_value,
                            requireNotNull(entry.state.path.component),
                            WakeText.Resource(types[index]),
                        ),
                    ),
                )
                actions[index]?.let { add(WakeFact(string.feature_wake_api_fact_action, WakeText.Literal(it))) }
                if (index == 1) {
                    add(WakeFact(appString.feature_appdetail_api_belonging_sdk, WakeText.Literal("Push SDK")))
                    add(WakeFact(appString.feature_appdetail_api_blocking_effect, WakeText.Literal("Stops push delivery")))
                }
            }
            assertThat(row.facts).containsExactlyElementsIn(facts).inOrder()
            assertThat(row.paths).containsExactly(entry.state)
            assertThat(row.canToggle(ui.background)).isTrue()
        }
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
        assertThat(ui.choices.map { it.key }).containsExactly(ui.methods.first().key, FORCE_STOP_CHOICE).inOrder()
        assertThat(ui.copy(background = BackgroundUsage(true, false)).choices).isEqualTo(ui.choices)
        assertThat(ui.copy(background = BackgroundUsage(true, true)).choices.map { it.key })
            .containsExactly(ui.methods.first().key, BACKGROUND_CHOICE, FORCE_STOP_CHOICE).inOrder()
        assertThat(ui.normal.parts()).containsExactly(
            WakeText.Count(plurals.feature_wake_api_normal_opened, 2L),
            WakeText.Count(plurals.feature_wake_api_normal_push, 8L),
        ).inOrder()
    }

    @Test
    fun givenDormantEntryAndDeepRestriction_whenBuildingChoices_thenOnlyOffersAvailableActions() {
        val entry = potentialEntry()
        val restriction = DeepBackgroundRestriction(false, false, false, changeable = true)
        val ui = buildWakeAppState(detailState(), null, emptyList(), emptyMap(), listOf(entry)).copy(deepRestriction = restriction)
        val key = ui.potential.single().key
        val forceStop = WakeSheetChoice(FORCE_STOP_CHOICE, WakeText.Resource(string.feature_wake_api_sheet_force_stop))

        assertThat(ui.canBlockAll).isTrue()
        assertThat(ui.choices).containsExactly(
            WakeSheetChoice(
                key,
                WakeText.Resource(string.feature_wake_api_choice_with_component, WakeText.Resource(string.feature_wake_api_potential_boot), WakeText.Literal("BootReceiver")),
            ),
            WakeSheetChoice(DEEP_RESTRICTION_CHOICE, WakeText.Resource(string.feature_wake_api_sheet_deep_restriction)),
            forceStop,
        ).inOrder()
        for (unavailable in listOf(restriction.copy(changeable = false), DeepBackgroundRestriction(true, true, true, changeable = true))) {
            assertThat(ui.copy(deepRestriction = unavailable).choices.map { it.key }).containsExactly(key, FORCE_STOP_CHOICE).inOrder()
        }
        assertThat(ui.copy(potential = emptyList(), deepRestriction = null).choices).isEmpty()
    }

    @Test
    fun givenOnlyStandaloneRestrictions_whenBuildingChoices_thenTheFabIsAvailable() {
        val empty = WakeAppUiState(WakeAppIdentity(DETAIL_PACKAGE), loaded = true)
        assertThat(empty.canBlockAll).isFalse()
        val background = empty.copy(background = BackgroundUsage(allowed = true, changeable = true))
        assertThat(background.choices.map { it.key }).containsExactly(BACKGROUND_CHOICE, FORCE_STOP_CHOICE).inOrder()
        assertThat(background.canBlockAll).isTrue()
        val deep = empty.copy(deepRestriction = DeepBackgroundRestriction(false, false, false, changeable = true))
        assertThat(deep.choices.map { it.key }).containsExactly(DEEP_RESTRICTION_CHOICE, FORCE_STOP_CHOICE).inOrder()
        assertThat(deep.canBlockAll).isTrue()
    }

    @Test
    fun givenBlockedPotentialService_whenFirewallEvidenceAppears_thenThePackageManagerRowRemainsControllable() {
        val entry = potentialEntry(PotentialWakeCategory.SCHEDULED_JOB, "Worker", ComponentType.SERVICE, action = null, block = WakePathBlock.USER)
        val component = requireNotNull(entry.state.path.component)
        val path = detailPath("peer-service", actor = "peer", component = component).copy(trigger = ReviveTrigger.PEER_SERVICE)
        val peer = WakePeerLink("peer", WakeDirection.INBOUND, listOf(path), emptyList())
        val rule = AutomaticWakeBlock("ifw", "peer", DETAIL_PACKAGE, component, "", active = true, allowed = false, method = "IFW")
        val state = detailState(peers = listOf(peer), automatic = listOf(rule))

        val ui = buildWakeAppState(state, null, emptyList(), emptyMap(), listOf(entry))
        val row = ui.potential.single()
        assertThat(row.paths.single().path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        assertThat(row.blocked).isTrue()
        assertThat(row.canToggle(ui.background)).isTrue()
        assertThat(ui.relations).hasSize(2)

        val resolved = state.paths.getValue(path.pathKey).copy(path = path.copy(blockMethod = WakeBlockMethod.COMPONENT))
        val resolvedPm = state.copy(paths = mapOf(path.pathKey to resolved))
        assertThat(buildWakeAppState(resolvedPm, null, emptyList(), emptyMap(), listOf(entry)).potential).isEmpty()
        val automaticPm = state.copy(automatic = listOf(rule.copy(method = "PM")))
        assertThat(buildWakeAppState(automaticPm, null, emptyList(), emptyMap(), listOf(entry)).potential).isEmpty()
    }

    @Test
    fun givenDuplicateShortClassNames_whenBuildingPotentialRows_thenBothExposePackageRelativeNames() {
        val entries = listOf(potentialEntry(name = "sync.BootReceiver"), potentialEntry(name = "metrics.BootReceiver"), potentialEntry(name = "RefreshReceiver"))
        val ui = buildWakeAppState(detailState(), null, emptyList(), emptyMap(), entries)
        val labels = listOf("sync.BootReceiver", "metrics.BootReceiver", "RefreshReceiver").map { WakeText.Literal(it) }
        val titles = labels.map {
            WakeText.Resource(string.feature_wake_api_choice_with_component, WakeText.Resource(string.feature_wake_api_potential_boot), it)
        }

        assertThat(ui.potential.map { it.meta }).containsExactlyElementsIn(labels).inOrder()
        assertThat(ui.potential.map { it.actionTitle }).containsExactlyElementsIn(titles).inOrder()
        assertThat(ui.choices.take(3).map { it.title }).containsExactlyElementsIn(titles).inOrder()
    }

    @Test
    fun givenDormantChoicesWithTheSameCategory_whenBuildingTitles_thenEachUsesItsOwnComponentName() {
        val entries = listOf(potentialEntry(name = "FirstReceiver"), potentialEntry(name = "SecondReceiver"))
        val ui = buildWakeAppState(detailState(), null, emptyList(), emptyMap(), entries)

        assertThat(ui.choices.take(2).map { it.title }).containsExactlyElementsIn(
            listOf("FirstReceiver", "SecondReceiver").map {
                WakeText.Resource(string.feature_wake_api_choice_with_component, WakeText.Resource(string.feature_wake_api_potential_boot), WakeText.Literal(it))
            },
        ).inOrder()
    }

    @Test
    fun givenBackgroundRestrictionOnly_whenCheckingControls_thenAllowsUnknownOrChangeableUsageWithoutADuplicateChoice() {
        val timer = detailPath("timer", method = WakeBlockMethod.BACKGROUND_RESTRICTION, component = null)
        val state = detailState(own = listOf(detailMethod(OwnWakeKind.TIMED, timer)))
        val ui = buildWakeAppState(state, null, emptyList(), emptyMap())
        val row = ui.methods.single()
        val changeable = ui.copy(background = BackgroundUsage(allowed = true, changeable = true))
        val protected = ui.copy(background = BackgroundUsage(allowed = true, changeable = false))

        assertThat(row.hasSwitch).isTrue()
        for (available in listOf(ui, changeable)) {
            assertThat(row.canToggle(available.background)).isTrue()
            assertThat(available.choices.map { it.key }).containsExactly(row.key, FORCE_STOP_CHOICE).inOrder()
            assertThat(available.canBlockAll).isTrue()
        }
        assertThat(row.canToggle(protected.background)).isFalse()
        assertThat(protected.choices).isEmpty()
        assertThat(protected.canBlockAll).isFalse()
    }

    @Test
    fun givenProtectedBackgroundAndComponentControl_whenCheckingControls_thenKeepsMixedRowActionable() {
        val timer = detailPath("timer", method = WakeBlockMethod.BACKGROUND_RESTRICTION, component = null)
        val job = detailPath("job", method = WakeBlockMethod.COMPONENT)
        val state = detailState(own = listOf(detailMethod(OwnWakeKind.TIMED, timer, job)))
        val ui = buildWakeAppState(state, null, emptyList(), emptyMap()).copy(background = BackgroundUsage(allowed = true, changeable = false))

        assertThat(ui.methods.single().canToggle(ui.background)).isTrue()
        assertThat(ui.choices.map { it.key }).containsExactly(ui.methods.single().key, FORCE_STOP_CHOICE).inOrder()
        assertThat(ui.canBlockAll).isTrue()
    }

    @Test
    fun givenUnnamedScreenOffPath_whenMappingFacts_thenExplainsBackgroundRestriction() {
        val wakelock = detailPath("wakelock", method = WakeBlockMethod.BACKGROUND_RESTRICTION, component = null)
            .copy(actor = null, trigger = null, hold = HoldCause.WAKELOCK)
        val state = detailState(own = listOf(detailMethod(OwnWakeKind.SCREEN_OFF_RUN, wakelock)))

        val ui = buildWakeAppState(state, null, emptyList(), emptyMap())

        assertThat(ui.methods.single().facts).containsExactly(
            WakeFact(string.feature_wake_api_fact_method, WakeText.Resource(string.feature_wake_api_fact_method_background)),
        )
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
