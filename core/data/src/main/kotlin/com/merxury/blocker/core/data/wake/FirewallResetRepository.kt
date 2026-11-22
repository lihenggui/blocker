// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.ManagedWakeReset
import javax.inject.Inject

class FirewallResetRepository @Inject constructor(
    private val authorization: WakeAuthorizationRepository,
    private val managed: ManagedWakeReset,
    private val manual: IfwFileSystem,
    private val firewall: IIntentFirewall,
) {
    /** Returns the number of manual files removed, or null if the requested scope needs more permission. */
    suspend fun reset(target: String?, onDelete: suspend (String, Int, Int) -> Unit): Int? {
        val transport = authorization.resetTransport() ?: return null
        val manualRulesRemain = managed.resetFirewall(target, transport)
        val packages = when {
            !manualRulesRemain -> emptyList()
            !authorization.canClearManualRules(transport) -> return null
            target != null -> listOf(target)
            else -> manual.listRuleFiles()
        }
        var removed = 0
        try {
            for ((index, pkg) in packages.withIndex()) {
                onDelete(pkg, index + 1, packages.size)
                if (manual.deleteRules(pkg)) removed++
            }
        } finally {
            firewall.resetCache()
        }
        return removed
    }
}
