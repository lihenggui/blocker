// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.os.Build
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.wake.AppWakeState
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import com.merxury.blocker.core.model.wake.PotentialWakeEntry
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeAppEnvironment
import com.merxury.blocker.core.model.wake.WakeBlockMethod
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
    val potential: List<WakeDetailRow> = emptyList(),
    val normal: JustifiedStarts = JustifiedStarts(0, 0, 0, 0),
    val background: BackgroundUsage? = null,
    val deepRestriction: DeepBackgroundRestriction? = null,
    val environment: WakeAppEnvironment = WakeAppEnvironment(),
    val busy: Boolean = false,
    val notice: WakeNotice? = null,
    val loaded: Boolean = false,
) {
    val rows get() = relations + methods
    val controllableRows get() = rows + potential
    val hasRestart get() = methods.any { it.kind == OwnWakeKind.RESTART_AFTER_CLOSE && !it.blocked }
    val inbound get() = chainSide(relations.filter { it.direction == WakeDirection.INBOUND || it.direction == WakeDirection.MUTUAL })
    val outbound get() = chainSide(relations.filter { it.direction == WakeDirection.OUTBOUND || it.direction == WakeDirection.MUTUAL })
    val blockableRows get() = controllableRows.filter { it.canToggle(background) && !it.blocked }
    val canEnableDeepRestriction get() = deepRestriction?.let { it.changeable && !it.active } == true
    val choices get() = buildList {
        val available = blockableRows
        available.forEach { add(WakeSheetChoice(it.key, it.actionTitle)) }
        if (background?.allowed == true && background.changeable &&
            available.none { row -> row.paths.any { it.canRestrictBackground(app.packageName) } }
        ) {
            add(WakeSheetChoice(BACKGROUND_CHOICE, WakeText.Resource(string.feature_wake_api_sheet_background)))
        }
        if (canEnableDeepRestriction) {
            add(WakeSheetChoice(DEEP_RESTRICTION_CHOICE, WakeText.Resource(string.feature_wake_api_sheet_deep_restriction)))
        }
        if (isNotEmpty()) {
            add(WakeSheetChoice(FORCE_STOP_CHOICE, WakeText.Resource(string.feature_wake_api_sheet_force_stop)))
        }
    }
    val canBlockAll get() = choices.any { it.key != FORCE_STOP_CHOICE }
}

internal const val BACKGROUND_CHOICE = "background"
internal const val DEEP_RESTRICTION_CHOICE = "deep-restriction"
internal const val FORCE_STOP_CHOICE = "force-stop"
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
    val reEnabledByApp: Boolean = false,
    val componentLabel: WakeText.Literal? = null,
) {
    val actionTitle get() = componentLabel?.let {
        WakeText.Resource(string.feature_wake_api_choice_with_component, title, it)
    } ?: title
    val hasSwitch get() = paths.isNotEmpty() || automatic != null
    val blocked get() = automatic?.active ?: (paths.isNotEmpty() && paths.all { it.block != WakePathBlock.NONE })

    fun canToggle(background: BackgroundUsage?) = hasSwitch && (
        automatic != null || paths.any { it.path.blockMethod != WakeBlockMethod.BACKGROUND_RESTRICTION } ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && background?.changeable != false)
        )
}

internal fun WakePathState.canRestrictBackground(packageName: String) = block == WakePathBlock.NONE && path.blockMethod == WakeBlockMethod.BACKGROUND_RESTRICTION && path.target == packageName

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
    potential: List<PotentialWakeEntry> = emptyList(),
): WakeAppUiState {
    val app = state.activity
    val apps = installedApps.associateBy { it.key.packageName }
    val facts = WakeFacts(app.packageName, apps, details)
    val observedComponents = state.observedComponentNames()
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
        val silentPush = method.paths.isNotEmpty() && method.paths.all { it.trigger == ReviveTrigger.PUSH_TRANSPORT }
        makeRow(
            key = "method:${method.kind}:${method.component}:${method.action}:${method.paths.firstOrNull()?.hold}",
            title = WakeText.Resource(if (silentPush) string.feature_wake_api_method_silent_push else methodTitle(method.kind, boot)),
            paths = method.paths,
            state = state,
            lastCheckedMillis = lastCheckedMillis,
            facts = facts.forPaths(method.paths),
            screenOff = method.kind == OwnWakeKind.SCREEN_OFF_RUN,
        ).copy(kind = method.kind, boot = boot)
    }
    // The scanner may still be emitting entries for the previous set of observed components.
    val dormant = potential.filterNot { it.state.path.component in observedComponents }
    val shortNameCounts = dormant.mapNotNull { it.state.path.component?.substringAfterLast('.') }.groupingBy { it }.eachCount()
    val relativeNameCounts = dormant.mapNotNull { entry ->
        val path = entry.state.path
        path.component?.removePrefix("${path.target}.")
    }.groupingBy { it }.eachCount()
    return WakeAppUiState(
        app = apps.identity(app.packageName),
        status = wakeStatus(state.allBlocked, app.verdict),
        backgroundStarts = app.backgroundStarts,
        relations = relations,
        methods = methods,
        potential = dormant.map { entry ->
            val path = entry.state.path
            val shortName = path.component?.substringAfterLast('.')
            val duplicateShort = (shortNameCounts[shortName] ?: 0) > 1
            val relative = path.component?.removePrefix("${path.target}.")
            val label = when {
                !duplicateShort -> shortName
                (relativeNameCounts[relative] ?: 0) > 1 -> path.component
                else -> relative
            }
            facts.potentialRow(entry, label?.let { WakeText.Literal(it) }, duplicateShort)
        },
        normal = app.justified,
        environment = state.environment,
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
    val blocked = blockedMeta(blockable, lastCheckedMillis)
    val reEnabled = blocked == null && blockable.any { it.reEnabledByApp }
    val meta = when {
        reEnabled -> WakeText.Resource(string.feature_wake_api_re_enabled_by_app)
        blocked != null -> blocked
        screenOff -> WakeText.Duration(paths.sumOf(WakePath::screenOffNanos))
        else -> paths.maxOfOrNull(WakePath::lastSeenMillis)?.let { WakeText.Latest(paths.sumOf(WakePath::count), it) }
    }
    return WakeDetailRow(key, title, meta, facts, paths = blockable, reEnabledByApp = reEnabled)
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

internal fun AppWakeState.observedComponentNames(): Set<String> = buildSet {
    fun addComponentPaths(observed: List<WakePath>) {
        for (path in observed) {
            if (path.target != activity.packageName) continue
            val method = paths[path.pathKey]?.path?.blockMethod ?: path.blockMethod
            if (method == WakeBlockMethod.COMPONENT) path.component?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
    activity.peers.forEach { link ->
        addComponentPaths(link.inbound)
        addComponentPaths(link.outbound)
    }
    activity.own.forEach { addComponentPaths(it.paths) }
    automatic.filter { it.method == "PM" && it.target == activity.packageName }
        .mapNotNullTo(this) { it.component.takeIf(String::isNotBlank) }
}

private fun List<WakePath>.firstNamedPath(): WakePath? = firstOrNull { !it.component.isNullOrBlank() }

private class WakeFacts(
    val packageName: String,
    val apps: Map<String, InstalledApp>,
    val details: Map<String, ComponentDetail>,
) {
    fun forPaths(paths: List<WakePath>, callers: List<String> = paths.mapNotNull { it.actor }): List<WakeFact> {
        val component = paths.firstNamedPath()
        val action = paths.firstNotNullOfOrNull { it.action?.takeIf(String::isNotBlank) }
        val facts = build(component?.component, component?.componentType, component?.target, callers, action)
        return if (component == null && action == null && paths.isNotEmpty() && paths.all { it.blockMethod == WakeBlockMethod.BACKGROUND_RESTRICTION }) {
            facts + WakeFact(string.feature_wake_api_fact_method, WakeText.Resource(string.feature_wake_api_fact_method_background))
        } else {
            facts
        }
    }

    fun automaticRow(block: AutomaticWakeBlock) = WakeDetailRow(
        key = "automatic:${block.pathId}",
        title = WakeText.Resource(string.feature_wake_api_method_known),
        meta = WakeText.Resource(string.feature_wake_api_blocked_automatically).takeIf { block.active },
        facts = build(block.component, ComponentType.SERVICE, block.target, listOf(block.caller), block.action),
        automatic = block,
    )

    fun potentialRow(entry: PotentialWakeEntry, componentLabel: WakeText.Literal?, duplicate: Boolean): WakeDetailRow {
        val state = entry.state
        val path = state.path
        val blocked = state.block != WakePathBlock.NONE
        val reEnabled = !blocked && state.reEnabledByApp
        val status = when {
            reEnabled -> WakeText.Resource(string.feature_wake_api_re_enabled_by_app)
            blocked -> WakeText.Resource(string.feature_wake_api_blocked)
            else -> null
        }
        return WakeDetailRow(
            key = "potential:${path.pathKey}",
            title = WakeText.Resource(entry.category.title),
            meta = if (duplicate && componentLabel != null && status != null) {
                WakeText.Resource(string.feature_wake_api_list_pair, componentLabel, status)
            } else {
                status ?: componentLabel
            },
            facts = build(path.component, path.componentType, path.target, emptyList(), path.action, qualifiedName = true),
            paths = listOf(state),
            reEnabledByApp = reEnabled,
            componentLabel = componentLabel,
        )
    }

    private fun build(
        name: String?,
        type: ComponentType?,
        target: String?,
        callers: List<String>,
        action: String?,
        qualifiedName: Boolean = false,
    ) = buildList {
        if (!name.isNullOrBlank()) {
            val displayName = if (qualifiedName) name else name.substringAfterLast('.')
            val value = when {
                type == null -> WakeText.Literal(displayName)

                target == null || target == packageName ->
                    WakeText.Resource(string.feature_wake_api_fact_component_value, displayName, WakeText.Resource(type.label))

                else ->
                    WakeText.Resource(string.feature_wake_api_fact_component_of_app, displayName, apps.identity(target).label, WakeText.Resource(type.label))
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

private val PotentialWakeCategory.title get() = when (this) {
    PotentialWakeCategory.BOOT_AUTOSTART -> string.feature_wake_api_potential_boot
    PotentialWakeCategory.PUSH_RECEIVER -> string.feature_wake_api_potential_push
    PotentialWakeCategory.SCHEDULED_JOB -> string.feature_wake_api_potential_job
    PotentialWakeCategory.ACCOUNT_SYNC -> string.feature_wake_api_potential_sync
    PotentialWakeCategory.SYSTEM_BROADCAST -> string.feature_wake_api_potential_system_broadcast
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
