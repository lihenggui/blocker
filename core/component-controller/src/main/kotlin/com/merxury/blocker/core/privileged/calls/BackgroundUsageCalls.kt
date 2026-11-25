// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.calls

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.os.Parcelable
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/** The app-op mode is per user. Both power allowlists are device-wide. */
@Parcelize
internal data class BackgroundUsageState(
    val mode: Int,
    val systemAllowlisted: Boolean,
    val userAllowlisted: Boolean,
) : Parcelable {
    fun toBackgroundUsage(): BackgroundUsage = BackgroundUsage(
        allowed = mode != MODE_IGNORED,
        changeable = !systemAllowlisted,
    )
}

/** Reads the app-op mode and both power allowlists for an installed app. */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ReadBackgroundUsage(
    val packageName: String,
    val user: UserId,
) : PrivilegedCall<BackgroundUsageState> {
    override fun run(system: SystemServices): BackgroundUsageState {
        val uid = backgroundUsageUid(system, packageName, user)
        return BackgroundUsageState(
            mode = system.appOps.checkOperation(OP_RUN_ANY_IN_BACKGROUND, uid, packageName),
            systemAllowlisted = packageName in system.deviceIdle.systemPowerWhitelist,
            userAllowlisted = packageName in system.deviceIdle.userPowerWhitelist,
        )
    }
}

/** Applies Android Settings' Restricted or Optimized mode. Returns false for system allowlist entries. */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class SetBackgroundUsageAllowed(
    val packageName: String,
    val user: UserId,
    val allowed: Boolean,
) : PrivilegedCall<Boolean> {
    override fun run(system: SystemServices): Boolean {
        val uid = backgroundUsageUid(system, packageName, user)
        val deviceIdle = system.deviceIdle
        if (packageName in deviceIdle.systemPowerWhitelist) return false

        // Android rejects removing some system exemptions even when there is no user entry.
        val userAllowlisted = packageName in deviceIdle.userPowerWhitelist
        val mode = if (allowed) MODE_ALLOWED else MODE_IGNORED
        system.appOps.setMode(OP_RUN_ANY_IN_BACKGROUND, uid, packageName, mode)
        if (userAllowlisted) deviceIdle.removePowerSaveWhitelistApp(packageName)
        return true
    }
}

internal val backgroundUsageSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun backgroundUsageUid(system: SystemServices, packageName: String, user: UserId): Int {
    if (!backgroundUsageSupported) throw UnsupportedOperationException("Background usage requires Android 9")
    return packageInfo(system, packageName, 0, user)?.applicationInfo?.uid
        ?: throw NameNotFoundException("$packageName is not installed for user ${user.value}")
}

// AppOpsManager.OP_RUN_ANY_IN_BACKGROUND is hidden from the public SDK.
internal const val OP_RUN_ANY_IN_BACKGROUND = 70
