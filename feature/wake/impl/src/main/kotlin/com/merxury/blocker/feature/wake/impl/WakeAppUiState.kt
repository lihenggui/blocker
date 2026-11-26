// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.wake.AppWakeState
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.model.wake.WakePeerLink
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.feature.appdetail.api.R.string as appString

internal data class WakeAppUiState(
    val app: WakeAppIdentity,
    val status: WakeAppStatus = WakeAppStatus.None,
    val backgroundStarts: Long = 0,
    val relations: List<WakeDetailRow> = emptyList(),
    val methods: List<WakeDetailRow> = emptyList(),
    val normal: JustifiedStarts = JustifiedStarts(0, 0, 0, 0),
    val background: BackgroundUsage? = null,
    val busy: Boolean = false,
    val notice: WakeNotice? = null,
    val loaded: Boolean = false,
) {
    val rows get() = relations + methods
    val hasRestart get() = methods.any { it.kind == OwnWakeKind.RESTART_AFTER_CLOSE && !it.blocked }
    val inbound get() = chainSide(relations.filter { it.direction == WakeDirection.INBOUND || it.direction == WakeDirection.MUTUAL })
    val outbound get() = chainSide(relations.filter { it.direction == WakeDirection.OUTBOUND || it.direction == WakeDirection.MUTUAL })
    val choices get() = buildList {
        rows.filter { it.canToggle && !it.blocked }.forEach { add(WakeSheetChoice(it.key, it.title)) }
        if (background?.allowed == true && background.changeable) {
            add(WakeSheetChoice(BACKGROUND_CHOICE, WakeText.Resource(string.feature_wake_api_sheet_background)))
        }
    }
    val canBlockAll get() = rows.any { it.canToggle && !it.blocked }
}

internal const val BACKGROUND_CHOICE = "background"
internal data class WakeSheetChoice(val key: String, val title: WakeText)
internal enum class WakeAppStatus(val halo: WakeHalo) {
    KeepAlive(WakeHalo.KeepAlive),
    Attention(WakeHalo.Attention),
    None(WakeHalo.Attention),
    AllBlocked(WakeHalo.Blocked),
}

// Turn the blocked halo by one lobe of the six-sided Material polygon.
private const val BLOCKED_HALO_ROTATION = 60f

internal enum class WakeHalo(val rotation: Float) { KeepAlive(0f), Attention(0f), Blocked(BLOCKED_HALO_ROTATION) }
internal data class WakeAppIdentity(val packageName: String, val label: String = packageName, val icon: ApkIcon? = null)

internal data class WakeDetailRow(
    val key: String,
    val title: WakeText,
    val meta: WakeText?,
    val facts: List<WakeFact>,
    val paths: List<WakePathState> = emptyList(),
    val automatic: AutomaticWakeBlock? = null,
    val peer: WakeAppIdentity? = null,
    val direction: WakeDirection? = null,
    val kind: OwnWakeKind? = null,
    val boot: Boolean = false,
) {
    val canToggle get() = paths.isNotEmpty() || automatic != null
    val blocked get() = automatic?.active ?: (paths.isNotEmpty() && paths.all { it.block != WakePathBlock.NONE })
}

internal data class WakeFact(val label: Int, val value: WakeText)
internal data class WakeChainPeer(val app: WakeAppIdentity, val blocked: Boolean)

// Keep three chain slots and give the last slot to the overflow count when needed.
private const val CHAIN_PEER_SLOTS = 3

internal data class WakeChainSide(val peers: List<WakeChainPeer>) {
    val cut get() = peers.isNotEmpty() && peers.all { it.blocked }
    val visiblePeers get() = if (peers.size > CHAIN_PEER_SLOTS) peers.take(CHAIN_PEER_SLOTS - 1) else peers
    val more get() = peers.size - visiblePeers.size
}

private fun chainSide(rows: List<WakeDetailRow>) = WakeChainSide(
    rows.mapNotNull { row ->
        row.peer?.let { WakeChainPeer(it, row.blocked) }
    },
)

internal fun buildWakeAppState(
    state: AppWakeState,
    lastCheckedMillis: Long?,
    installedApps: List<InstalledApp>,
    details: Map<String, ComponentDetail>,
): WakeAppUiState {
    val app = state.activity
    val apps = installedApps.associateBy { it.key.packageName }
    val facts = WakeFacts(app.packageName, apps, details)
    val relations = app.peers.sortedWith(
        compareBy<WakePeerLink> { relationOrder(it.direction) }.thenByDescending { it.inbound.sumOf(WakePath::count) + it.outbound.sumOf(WakePath::count) },
    ).map { link ->
        val peer = apps.identity(link.peer)
        val paths = link.inbound + link.outbound
        makeRow(
            key = "peer:${link.peer}",
            title = WakeText.Resource(relationTitle(link.direction), peer.label),
            paths = paths,
            state = state,
            lastCheckedMillis = lastCheckedMillis,
            facts = facts.forPaths(paths, relationCallers(app.packageName, link.peer, link.direction)),
        ).copy(peer = peer, direction = link.direction)
    } + state.automatic.map(facts::automaticRow)
    val methods = app.own.map { method ->
        val boot = method.action.isWakeBootAction() || method.paths.any { it.action.isWakeBootAction() }
        makeRow(
            key = "method:${method.kind}:${method.component}:${method.action}:${method.paths.firstOrNull()?.hold}",
            title = WakeText.Resource(methodTitle(method.kind, boot)),
            paths = method.paths,
            state = state,
            lastCheckedMillis = lastCheckedMillis,
            facts = facts.forPaths(method.paths),
            screenOff = method.kind == OwnWakeKind.SCREEN_OFF_RUN,
        ).copy(kind = method.kind, boot = boot)
    }
    return WakeAppUiState(
        app = apps.identity(app.packageName),
        status = wakeStatus(state.allBlocked, app.verdict),
        backgroundStarts = app.backgroundStarts,
        relations = relations,
        methods = methods,
        normal = app.justified,
        loaded = true,
    )
}

internal fun wakeStatus(allBlocked: Boolean, verdict: WakeVerdict): WakeAppStatus = when {
    allBlocked -> WakeAppStatus.AllBlocked
    verdict == WakeVerdict.KEEP_ALIVE -> WakeAppStatus.KeepAlive
    verdict == WakeVerdict.SUSPICIOUS -> WakeAppStatus.Attention
    else -> WakeAppStatus.None
}

private fun makeRow(
    key: String,
    title: WakeText,
    paths: List<WakePath>,
    state: AppWakeState,
    lastCheckedMillis: Long?,
    facts: List<WakeFact>,
    screenOff: Boolean = false,
): WakeDetailRow {
    val blockable = paths.mapNotNull { state.paths[it.pathKey] }.distinctBy { it.path.pathKey }
    val meta = blockedMeta(blockable, lastCheckedMillis) ?: when {
        screenOff -> WakeText.Duration(paths.sumOf(WakePath::screenOffNanos))
        else -> paths.maxOfOrNull(WakePath::lastSeenMillis)?.let { WakeText.Latest(paths.sumOf(WakePath::count), it) }
    }
    return WakeDetailRow(key, title, meta, facts, paths = blockable)
}

private fun blockedMeta(paths: List<WakePathState>, lastCheckedMillis: Long?): WakeText? = when {
    paths.isEmpty() || paths.any { it.block == WakePathBlock.NONE } -> null

    paths.all { it.block == WakePathBlock.AUTOMATIC } -> WakeText.Resource(string.feature_wake_api_blocked_automatically)

    paths.all { path ->
        path.block == WakePathBlock.USER && !path.seenSinceBlock &&
            path.blockedAtMillis?.let { lastCheckedMillis != null && lastCheckedMillis > it } == true
    } -> WakeText.Resource(string.feature_wake_api_blocked_not_seen)

    else -> WakeText.Resource(string.feature_wake_api_blocked)
}

internal fun AppWakeState.visibleComponentNames(): Set<String> = buildSet {
    activity.peers.forEach { link -> (link.inbound + link.outbound).firstNamedPath()?.component?.let(::add) }
    activity.own.forEach { method -> method.paths.firstNamedPath()?.component?.let(::add) }
    automatic.forEach { add(it.component) }
}

private fun List<WakePath>.firstNamedPath(): WakePath? = firstOrNull { !it.component.isNullOrBlank() }

private class WakeFacts(
    val packageName: String,
    val apps: Map<String, InstalledApp>,
    val details: Map<String, ComponentDetail>,
) {
    fun forPaths(paths: List<WakePath>, callers: List<String> = paths.mapNotNull { it.actor }): List<WakeFact> {
        val component = paths.firstNamedPath()
        return build(component?.component, component?.componentType, component?.target, callers, paths.firstNotNullOfOrNull { it.action })
    }

    fun automaticRow(block: AutomaticWakeBlock) = WakeDetailRow(
        key = "automatic:${block.pathId}",
        title = WakeText.Resource(string.feature_wake_api_method_known),
        meta = WakeText.Resource(string.feature_wake_api_blocked_automatically).takeIf { block.active },
        facts = build(block.component, ComponentType.SERVICE, block.target, listOf(block.caller), block.action),
        automatic = block,
    )

    private fun build(name: String?, type: ComponentType?, target: String?, callers: List<String>, action: String?) = buildList {
        if (!name.isNullOrBlank()) {
            val short = name.substringAfterLast('.')
            val value = when {
                type == null -> WakeText.Literal(short)

                target == null || target == packageName ->
                    WakeText.Resource(string.feature_wake_api_fact_component_value, short, WakeText.Resource(type.label))

                else ->
                    WakeText.Resource(string.feature_wake_api_fact_component_of_app, short, apps.identity(target).label, WakeText.Resource(type.label))
            }
            add(WakeFact(string.feature_wake_api_fact_component, value))
        }
        if (callers.isNotEmpty()) {
            add(WakeFact(string.feature_wake_api_fact_caller, WakeText.Callers(callers.distinct().map { apps.identity(it).label })))
        }
        if (!action.isNullOrBlank()) {
            add(WakeFact(string.feature_wake_api_fact_action, WakeText.Literal(action.removePrefix("android.intent.action."))))
        }
        val detail = details[name]
        detail?.sdkName?.takeIf(String::isNotBlank)?.let {
            add(WakeFact(appString.feature_appdetail_api_belonging_sdk, WakeText.Literal(it)))
        }
        detail?.disableEffect?.takeIf(String::isNotBlank)?.let {
            add(WakeFact(appString.feature_appdetail_api_blocking_effect, WakeText.Literal(it)))
        }
    }
}

private fun Map<String, InstalledApp>.identity(packageName: String): WakeAppIdentity = this[packageName]?.let {
    WakeAppIdentity(packageName, it.label, it.wakeIcon())
} ?: WakeAppIdentity(packageName)

private fun relationOrder(direction: WakeDirection) = when (direction) {
    WakeDirection.MUTUAL -> 0
    WakeDirection.INBOUND -> 1
    WakeDirection.OUTBOUND -> 2
}

private fun relationCallers(app: String, peer: String, direction: WakeDirection) = when (direction) {
    WakeDirection.MUTUAL -> listOf(peer, app)
    WakeDirection.INBOUND -> listOf(peer)
    WakeDirection.OUTBOUND -> listOf(app)
}

internal fun relationTitle(direction: WakeDirection) = when (direction) {
    WakeDirection.MUTUAL -> string.feature_wake_api_relation_mutual
    WakeDirection.INBOUND -> string.feature_wake_api_relation_inbound
    WakeDirection.OUTBOUND -> string.feature_wake_api_relation_outbound
}

internal fun methodTitle(kind: OwnWakeKind, boot: Boolean) = when (kind) {
    OwnWakeKind.RESTART_AFTER_CLOSE -> string.feature_wake_api_method_restart
    OwnWakeKind.SELF_START -> string.feature_wake_api_method_self_start
    OwnWakeKind.SYSTEM_EVENT -> if (boot) string.feature_wake_api_reason_boot else string.feature_wake_api_method_system_event
    OwnWakeKind.TIMED -> string.feature_wake_api_method_timed
    OwnWakeKind.SCREEN_OFF_RUN -> string.feature_wake_api_method_screen_off
}

private val ComponentType.label get() = when (this) {
    ComponentType.ACTIVITY -> string.feature_wake_api_type_activity
    ComponentType.SERVICE -> string.feature_wake_api_type_service
    ComponentType.RECEIVER -> string.feature_wake_api_type_receiver
    ComponentType.PROVIDER -> string.feature_wake_api_type_provider
}

internal fun JustifiedStarts.parts(): List<WakeText.Count> = listOf(
    WakeText.Count(plurals.feature_wake_api_normal_opened, userInitiated),
    WakeText.Count(plurals.feature_wake_api_normal_notification, notification),
    WakeText.Count(plurals.feature_wake_api_normal_push, push),
    WakeText.Count(plurals.feature_wake_api_normal_granted, grantedRole),
).filter { it.amount > 0 }
