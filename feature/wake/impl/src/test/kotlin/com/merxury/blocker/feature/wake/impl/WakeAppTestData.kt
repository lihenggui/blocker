// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.wake.AppWakeActivity
import com.merxury.blocker.core.model.wake.AppWakeState
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.OwnWakeMethod
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.model.wake.WakePeerLink
import com.merxury.blocker.core.model.wake.WakeVerdict

internal const val DETAIL_PACKAGE = "example.calendar"

internal fun detailPath(
    key: String,
    target: String = DETAIL_PACKAGE,
    actor: String = DETAIL_PACKAGE,
    count: Long = 1,
    last: Long = 10,
    method: WakeBlockMethod = WakeBlockMethod.FIREWALL_RULE,
    action: String? = null,
    component: String? = "$target.Service",
    screenOff: Long = 0,
) = WakePath(
    key, target, actor, ReviveTrigger.SELF_PROCESS, null, component,
    ComponentType.SERVICE.takeIf { component != null }, action, count, 0, screenOff, last, false, method,
)

internal fun detailState(
    peers: List<WakePeerLink> = emptyList(),
    own: List<OwnWakeMethod> = emptyList(),
    blocks: Map<String, WakePathState> = emptyMap(),
    automatic: List<AutomaticWakeBlock> = emptyList(),
    verdict: WakeVerdict = WakeVerdict.KEEP_ALIVE,
    allBlocked: Boolean = false,
    normal: JustifiedStarts = JustifiedStarts(2, 0, 8, 0),
): AppWakeState {
    val paths = peers.flatMap { it.inbound + it.outbound } + own.flatMap { it.paths }
    val blockable = paths.filter { it.blockMethod != WakeBlockMethod.NONE }
        .associate { it.pathKey to WakePathState(it, WakePathBlock.NONE, null, false) }
    return AppWakeState(AppWakeActivity(DETAIL_PACKAGE, verdict, 12, peers, own, normal), blockable + blocks, automatic, allBlocked)
}

internal fun detailMethod(kind: OwnWakeKind, vararg paths: WakePath) = OwnWakeMethod(kind, paths.firstOrNull()?.component, paths.firstOrNull()?.action, paths.toList())
