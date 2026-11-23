// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.ManagedWakeReset
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

/**
 * Resets Intent Firewall rules for the "Reset IFW" action: the rule file of one package or of
 * every package, then the wake feature's own files, as the wake feature resets them.
 *
 * Package rule files are deleted through [rules], over the privileged channel of the chosen
 * controller type, so Shizuku running as root or the system uid can reset them and no su prompt
 * appears when the controller type is Shizuku. They are deleted without being read, so a damaged
 * file goes too; files not named after a package, such as a user's own, are kept. They go first
 * because deleting them connects the channel, which may obtain the Shizuku grant the wake feature
 * then uses for its files.
 */
class FirewallResetRepository @Inject constructor(
    private val authorization: WakeAuthorizationRepository,
    private val managed: ManagedWakeReset,
    private val rules: IfwStore,
    private val firewall: IIntentFirewall,
) {
    /**
     * Resets the rules of [target], or of every package when null, reporting each package rule
     * file with [onDelete] (package, position from 1, count) before deleting it.
     *
     * Returns the number of package rule files removed, or null when the rule directory cannot be
     * reached through the chosen channel. Throws [IOException] when deleting a file fails; files
     * deleted before it stay deleted. The wake feature's files are reset in every case.
     */
    suspend fun reset(target: String?, onDelete: suspend (String, Int, Int) -> Unit): Int? {
        try {
            val removed = try {
                resetPackageFiles(target, onDelete)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    resetWakeFiles(target)
                } catch (wake: CancellationException) {
                    throw wake
                } catch (wake: Exception) {
                    e.addSuppressed(wake)
                }
                throw e
            }
            resetWakeFiles(target)
            return removed
        } finally {
            // Screens not yet cut over to IfwStore still read through IIntentFirewall and its
            // cache; drop this once that cutover is done.
            firewall.resetCache()
        }
    }

    private suspend fun resetPackageFiles(target: String?, onDelete: suspend (String, Int, Int) -> Unit): Int? {
        val packages = when {
            target != null -> listOf(target)

            else -> when (val listed = rules.packagesWithRuleFiles()) {
                is IfwResult.Success -> listed.value
                is IfwResult.Failure -> return null
            }
        }
        var removed = 0
        for ((index, pkg) in packages.withIndex()) {
            onDelete(pkg, index + 1, packages.size)
            when (val cleared = rules.clear(pkg)) {
                is IfwResult.Success -> if (cleared.value) removed++

                is IfwResult.Failure -> {
                    if (cleared.reason == IfwFailure.NoAccess) return null
                    throw IOException("Cannot delete the IFW rules of $pkg: ${cleared.reason}")
                }
            }
        }
        return removed
    }

    // Without its own access the wake feature cannot have written files. Its answer, whether
    // package files remain, no longer matters: they are already gone.
    private suspend fun resetWakeFiles(target: String?) {
        authorization.resetTransport()?.let { managed.resetFirewall(target, it) }
    }
}
