// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.ui.coil.ApkIcon

internal sealed interface WakeOverviewUiState {
    data object Loading : WakeOverviewUiState
    data object NoAccess : WakeOverviewUiState
    data object NotOwnerProfile : WakeOverviewUiState
    data object ReadsRefused : WakeOverviewUiState
    data class Collecting(val checking: Boolean) : WakeOverviewUiState
    data class Ready(
        val keepAlive: List<WakeAppRow>,
        val attention: List<WakeAppRow>,
        val handledKeepAlive: Int,
        val automaticBlocks: Int,
        val otherApps: Int,
        val checking: Boolean,
    ) : WakeOverviewUiState
}

internal data class WakeAppRow(
    val packageName: String,
    val label: String,
    val icon: ApkIcon?,
    val headline: WakeHeadline,
)

internal fun wakeOverviewUiState(
    availability: WakeAvailability?,
    checking: Boolean,
    summaries: List<AppWakeSummary> = emptyList(),
    automaticBlocks: List<AutomaticWakeBlock> = emptyList(),
    installedApps: List<InstalledApp> = emptyList(),
): WakeOverviewUiState = when (availability) {
    null -> WakeOverviewUiState.Loading
    WakeAvailability.NoAccess -> WakeOverviewUiState.NoAccess
    WakeAvailability.NotOwnerProfile -> WakeOverviewUiState.NotOwnerProfile
    WakeAvailability.ReadsRefused -> WakeOverviewUiState.ReadsRefused
    WakeAvailability.Collecting -> WakeOverviewUiState.Collecting(checking)
    is WakeAvailability.Ready -> readyOverview(summaries, automaticBlocks, installedApps, checking)
}

private fun readyOverview(
    summaries: List<AppWakeSummary>,
    automaticBlocks: List<AutomaticWakeBlock>,
    installedApps: List<InstalledApp>,
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

    return WakeOverviewUiState.Ready(
        keepAlive = keepAlive,
        attention = attention,
        handledKeepAlive = handledKeepAlive,
        automaticBlocks = automaticBlocks.count { it.active },
        otherApps = apps.size - keepAlive.size - attention.size,
        checking = checking,
    )
}

internal fun InstalledApp.wakeIcon() = ApkIcon(key.packageName, apkPath, versionCode, uid)
