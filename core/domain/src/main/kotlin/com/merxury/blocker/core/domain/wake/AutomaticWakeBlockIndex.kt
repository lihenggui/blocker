// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent

internal class AutomaticWakeBlockIndex(blocks: List<AutomaticWakeBlock>) {
    private val byComponent = blocks.groupBy { block ->
        Identity(block.caller, block.target, qualifiedWakeComponent(block.target, block.component))
    }

    /** Matches the caller, target and component, and requires the action when the path supplies one. */
    fun matching(path: WakePath): List<AutomaticWakeBlock> {
        val matches = byComponent[Identity(path.actor, path.target, qualifiedWakeComponent(path.target, path.component))].orEmpty()
        return if (path.action.isNullOrEmpty()) matches else matches.filter { it.action == path.action }
    }

    private data class Identity(val caller: String?, val target: String, val component: String?)
}
