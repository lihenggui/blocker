// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.ui.coil.ApkIcon

internal sealed interface WakeOverviewUiState {
    data object Loading : WakeOverviewUiState
    data object NoAccess : WakeOverviewUiState
    data object NotOwnerProfile : WakeOverviewUiState
    data object ReadsRefused : WakeOverviewUiState
    data class Collecting(
        val checking: Boolean,
        val installedApps: List<WakeAppIdentity> = emptyList(),
    ) : WakeOverviewUiState

    data class Ready(
        val keepAlive: List<WakeAppRow>,
        val attention: List<WakeAppRow>,
        val handledKeepAlive: Int,
        val automaticBlocks: Int,
        val otherApps: Int,
        val checking: Boolean,
        val blockedApps: List<WakeAppRow> = emptyList(),
        val dormantApps: List<WakeAppIdentity> = emptyList(),
    ) : WakeOverviewUiState {
        val hasSearchableApps get() = keepAlive.isNotEmpty() || attention.isNotEmpty() || blockedApps.isNotEmpty() || dormantApps.isNotEmpty()
    }
}

internal data class WakeAppRow(
    val packageName: String,
    val label: String,
    val icon: ApkIcon?,
    val headline: WakeHeadline?,
)

internal enum class WakeSearchGroup { KeepAlive, Attention, Blocked, Dormant }

internal data class WakeSearchRow(val app: WakeAppIdentity, val group: WakeSearchGroup)

internal fun WakeOverviewUiState.Ready.searchResults(query: String): List<WakeSearchRow> = buildList {
    val term = query.trim()
    fun addMatches(apps: List<WakeAppRow>, group: WakeSearchGroup) {
        apps.filter { matchesWakeSearch(it.label, it.packageName, term) }
            .mapTo(this) { WakeSearchRow(WakeAppIdentity(it.packageName, it.label, it.icon), group) }
    }
    addMatches(keepAlive, WakeSearchGroup.KeepAlive)
    addMatches(attention, WakeSearchGroup.Attention)
    addMatches(blockedApps, WakeSearchGroup.Blocked)
    dormantApps.filter { matchesWakeSearch(it.label, it.packageName, term) }
        .mapTo(this) { WakeSearchRow(it, WakeSearchGroup.Dormant) }
}

private fun matchesWakeSearch(label: String, packageName: String, term: String) = label.contains(term, ignoreCase = true) || packageName.contains(term, ignoreCase = true)

internal fun wakeOverviewUiState(
    availability: WakeAvailability?,
    checking: Boolean,
    summaries: List<AppWakeSummary> = emptyList(),
    automaticBlocks: List<AutomaticWakeBlock> = emptyList(),
    installedApps: List<InstalledApp> = emptyList(),
    wakeBlocks: List<WakeBlock> = emptyList(),
): WakeOverviewUiState = when (availability) {
    null -> WakeOverviewUiState.Loading

    WakeAvailability.NoAccess -> WakeOverviewUiState.NoAccess

    WakeAvailability.NotOwnerProfile -> WakeOverviewUiState.NotOwnerProfile

    WakeAvailability.ReadsRefused -> WakeOverviewUiState.ReadsRefused

    WakeAvailability.Collecting -> WakeOverviewUiState.Collecting(
        checking,
        installedApps.sortedBy { it.label.lowercase() }.map(InstalledApp::wakeIdentity),
    )

    is WakeAvailability.Ready -> readyOverview(summaries, automaticBlocks, installedApps, wakeBlocks, checking)
}

private fun readyOverview(
    summaries: List<AppWakeSummary>,
    automaticBlocks: List<AutomaticWakeBlock>,
    installedApps: List<InstalledApp>,
    wakeBlocks: List<WakeBlock>,
    checking: Boolean,
): WakeOverviewUiState.Ready {
    val apps = installedApps.associateBy { it.key.packageName }
    val keepAlive = mutableListOf<WakeAppRow>()
    val attention = mutableListOf<WakeAppRow>()
    var handledKeepAlive = 0

    for (summary in summaries) {
        val app = apps[summary.packageName] ?: continue
        if (summary.allBlocked) {
            if (summary.verdict == WakeVerdict.KEEP_ALIVE) handledKeepAlive++
            continue
        }
        val rows = when (summary.verdict) {
            WakeVerdict.KEEP_ALIVE -> keepAlive
            WakeVerdict.SUSPICIOUS -> attention
            WakeVerdict.NORMAL -> continue
        }
        rows += WakeAppRow(
            packageName = summary.packageName,
            label = app.label,
            icon = app.wakeIcon(),
            headline = summary.headline,
        )
    }

    val activeAutomatic = automaticBlocks.filter { it.active }
    val blockedPackages = buildSet {
        summaries.filter { it.allBlocked }.mapTo(this) { it.packageName }
        for (block in wakeBlocks) {
            add(block.target)
            block.sender?.let(::add)
        }
        for (block in activeAutomatic) {
            add(block.target)
            add(block.caller)
        }
    }
    val visiblePackages = (keepAlive + attention).mapTo(mutableSetOf()) { it.packageName }
    val summariesByPackage = summaries.associateBy { it.packageName }
    val remaining = apps.values.filter { it.key.packageName !in visiblePackages }.sortedBy { it.label.lowercase() }
    val (blocked, dormant) = remaining.partition { it.key.packageName in blockedPackages }
    val blockedApps = blocked.map { app ->
        val name = app.key.packageName
        WakeAppRow(name, app.label, app.wakeIcon(), summariesByPackage[name]?.headline)
    }
    val dormantApps = dormant.map(InstalledApp::wakeIdentity)

    return WakeOverviewUiState.Ready(
        keepAlive = keepAlive,
        attention = attention,
        handledKeepAlive = handledKeepAlive,
        automaticBlocks = activeAutomatic.size,
        otherApps = dormantApps.size,
        checking = checking,
        blockedApps = blockedApps,
        dormantApps = dormantApps,
    )
}

internal fun InstalledApp.wakeIcon() = ApkIcon(key.packageName, apkPath, versionCode, uid)

private fun InstalledApp.wakeIdentity() = WakeAppIdentity(key.packageName, label, wakeIcon())
