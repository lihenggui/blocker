// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import com.merxury.blocker.core.extension.MATCH_ALL_DIRECT_BOOT_COMPONENTS
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface WakeReceiverResolver {
    /** Resolves the target's manifest receivers for [action] in the owner profile. */
    fun resolve(target: String, action: String): List<ComponentDeclaration>
}

internal class PackageManagerWakeReceiverResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WakeReceiverResolver {
    @Suppress("DEPRECATION")
    override fun resolve(target: String, action: String): List<ComponentDeclaration> {
        val intent = Intent(action).setPackage(target)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or MATCH_ALL_DIRECT_BOOT_COMPONENTS
        val pm = context.packageManager
        val matches = pm.queryBroadcastReceivers(intent, flags) + broadcastData(action, target)?.let { data ->
            pm.queryBroadcastReceivers(Intent(intent).setData(data), flags)
        }.orEmpty()
        return matches.mapNotNull { match ->
            val info = match.activityInfo ?: return@mapNotNull null
            ComponentDeclaration(ComponentId(info.packageName, info.name), ComponentType.RECEIVER, info.exported, info.enabled)
        }.distinctBy { it.id }
    }
}

// The wake finding keeps the action. Probe the data scheme supplied by Android for that action.
@Suppress("DEPRECATION")
private fun broadcastData(action: String, target: String): Uri? = when (action) {
    Intent.ACTION_PACKAGE_ADDED,
    Intent.ACTION_PACKAGE_CHANGED,
    Intent.ACTION_PACKAGE_DATA_CLEARED,
    Intent.ACTION_PACKAGE_FIRST_LAUNCH,
    Intent.ACTION_PACKAGE_FULLY_REMOVED,
    Intent.ACTION_PACKAGE_REMOVED,
    Intent.ACTION_PACKAGE_REPLACED,
    Intent.ACTION_PACKAGE_RESTARTED,
    -> Uri.fromParts("package", target, null)

    Intent.ACTION_MEDIA_BAD_REMOVAL,
    Intent.ACTION_MEDIA_CHECKING,
    Intent.ACTION_MEDIA_EJECT,
    Intent.ACTION_MEDIA_MOUNTED,
    Intent.ACTION_MEDIA_NOFS,
    Intent.ACTION_MEDIA_REMOVED,
    Intent.ACTION_MEDIA_SCANNER_FINISHED,
    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
    Intent.ACTION_MEDIA_SCANNER_STARTED,
    Intent.ACTION_MEDIA_SHARED,
    Intent.ACTION_MEDIA_UNMOUNTABLE,
    Intent.ACTION_MEDIA_UNMOUNTED,
    -> "file:///storage/emulated/0".toUri()

    else -> null
}
