// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import timber.log.Timber
import java.io.IOException

/**
 * Whether [packageName]'s IFW rule file exists but cannot be read.
 *
 * IFW leaves such a file untouched and reports the requested changes as not applied. It also
 * skips providers and components the app no longer has, so call this after some changes were
 * not applied to tell a refused package from a legitimate skip.
 *
 * @throws com.merxury.blocker.core.exception.RootUnavailableException if root is unavailable
 */
internal suspend fun IIntentFirewall.hasUnreadableRules(packageName: String): Boolean = try {
    getRules(packageName)
    false
} catch (e: IOException) {
    Timber.w(e, "IFW rules for $packageName cannot be read")
    true
}

/**
 * Reads IFW rules strictly for one backup or restore run.
 *
 * Unlike [IIntentFirewall.getComponentEnableState], an unreadable rule file throws instead of
 * reading as "enabled". Without root the states are unknown rather than broken, so [read] returns
 * null, and keeps doing so for the rest of the run without probing root again, because each probe
 * can start su.
 */
internal class IfwRunReader(private val intentFirewall: IIntentFirewall) {
    /** Whether root was found unavailable during this run. */
    var rootUnavailable = false
        private set

    /**
     * Returns whether each of [packageName]'s components is enabled, that is, not blocked by a
     * component filter, or null if root is unavailable.
     *
     * @throws IOException if the rule file exists but cannot be read or parsed
     */
    suspend fun read(packageName: String): ((componentPackage: String, componentName: String) -> Boolean)? {
        if (rootUnavailable) return null
        val rules = try {
            intentFirewall.getRules(packageName)
        } catch (e: RootUnavailableException) {
            rootUnavailable = true
            return null
        }
        return { componentPackage, componentName ->
            val name = "$componentPackage/$componentName"
            IfwComponentType.entries.none { type -> name in rules.componentFiltersFor(type) }
        }
    }
}

/**
 * Backup entries for one component: its IFW state when known, then its PackageManager state.
 * An unknown IFW state is left out rather than written as "enabled".
 */
internal fun backupEntries(
    packageName: String,
    name: String,
    type: ComponentType,
    ifwEnabled: Boolean?,
    pmEnabled: Boolean,
): List<ComponentRule> = buildList {
    if (ifwEnabled != null) add(ComponentRule(packageName, name, ifwEnabled, type, IFW))
    add(ComponentRule(packageName, name, pmEnabled, type, PM))
}
