// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.ManagedWakeReset
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Resets Intent Firewall rules for the "Reset IFW" action: the rule file of one package, or every
 * rule file, then the wake feature's own files, as the wake feature resets them.
 *
 * Rule files are deleted through [rules], over the privileged channel of the chosen controller
 * type, so Shizuku running as root or the system uid can reset them and no su prompt appears when
 * the controller type is Shizuku. They are deleted without being read, so a damaged file goes too.
 * A reset of every rule file also removes those the global rule editor stored under names of their
 * own; a reset of one package touches only that package's file. They go first because deleting
 * them connects the channel, which may show its prompt; the wake feature's files then go through
 * the same channel without asking again.
 */
class LocalFirewallResetRepository @Inject constructor(
    private val managed: ManagedWakeReset,
    private val rules: IfwStore,
) : FirewallResetRepository {
    override suspend fun reset(target: String?, onDelete: suspend (String, Int, Int) -> Unit): Int? {
        val removed = try {
            resetRuleFiles(target, onDelete)
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
    }

    private suspend fun resetRuleFiles(target: String?, onDelete: suspend (String, Int, Int) -> Unit): Int? {
        val names = when {
            target != null -> listOf(target)

            else -> when (val listed = rules.ruleFileNames()) {
                is IfwResult.Success -> listed.value
                is IfwResult.Failure -> return null
            }
        }
        var removed = 0
        for ((index, name) in names.withIndex()) {
            onDelete(name, index + 1, names.size)
            when (val cleared = rules.clear(name)) {
                is IfwResult.Success -> if (cleared.value) removed++

                is IfwResult.Failure -> {
                    if (cleared.reason == IfwFailure.NoAccess) return null
                    throw IOException("Cannot delete the IFW rules in $name.xml: ${cleared.reason}")
                }
            }
        }
        return removed
    }

    /** Resets the wake feature's files; a channel that may not write them cannot have written any. */
    private suspend fun resetWakeFiles(target: String?) {
        if (!managed.resetFirewall(target)) Timber.i("Wake rules not reset: the privileged channel cannot write them")
    }
}
