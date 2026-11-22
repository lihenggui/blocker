// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

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
 * Reads [packageName]'s IFW rules once and returns whether a component is enabled, that is, not
 * blocked by a component filter.
 *
 * Unlike [IIntentFirewall.getComponentEnableState], this throws instead of guessing "enabled", so
 * nothing is persisted from a state that was not read.
 *
 * @throws com.merxury.blocker.core.exception.RootUnavailableException if root is unavailable
 * @throws IOException if the rule file exists but cannot be read or parsed
 */
internal suspend fun IIntentFirewall.readIfwEnabledStates(
    packageName: String,
): (componentPackage: String, componentName: String) -> Boolean {
    val rules = getRules(packageName)
    return { componentPackage, componentName ->
        val name = "$componentPackage/$componentName"
        IfwComponentType.entries.none { type -> name in rules.componentFiltersFor(type) }
    }
}
