// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.merxury.blocker.core.extension.MATCH_ALL_DIRECT_BOOT_COMPONENTS
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal fun interface WakeReceiverResolver {
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
        return context.packageManager.queryBroadcastReceivers(intent, flags).mapNotNull { match ->
            val info = match.activityInfo ?: return@mapNotNull null
            ComponentDeclaration(ComponentId(info.packageName, info.name), ComponentType.RECEIVER, info.exported, info.enabled)
        }.distinctBy { it.id }
    }
}
