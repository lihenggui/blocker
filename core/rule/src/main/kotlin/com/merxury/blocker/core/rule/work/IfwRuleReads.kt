// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.merxury.core.ifw.IIntentFirewall
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

