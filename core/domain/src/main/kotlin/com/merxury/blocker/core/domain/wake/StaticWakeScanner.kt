// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.ManifestComponent
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import com.merxury.blocker.core.model.wake.PotentialWakeCategory.ACCOUNT_SYNC
import com.merxury.blocker.core.model.wake.PotentialWakeCategory.BOOT_AUTOSTART
import com.merxury.blocker.core.model.wake.PotentialWakeCategory.PUSH_RECEIVER
import com.merxury.blocker.core.model.wake.PotentialWakeCategory.SCHEDULED_JOB
import com.merxury.blocker.core.model.wake.PotentialWakeCategory.SYSTEM_BROADCAST
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent

internal data class StaticWakeDeclaration(
    val packageName: String,
    val component: String,
    val componentType: ComponentType,
    val category: PotentialWakeCategory,
    val action: String?,
    val enabledInManifest: Boolean,
)

internal object StaticWakeScanner {
    fun scan(manifest: AndroidManifest): List<StaticWakeDeclaration> {
        val packageName = manifest.packageName
        val receivers = manifest.application.receivers.mapNotNull { receiver ->
            val actions = receiver.intentFilters.flatMap { it.actions }
            val boot = actions.firstOrNull(bootActions::contains)
            val push = actions.firstOrNull(pushActions::contains)
            val system = actions.firstOrNull {
                it in systemActions || (it == CONNECTIVITY_CHANGE && manifest.targetSdkVersion?.let { sdk -> sdk < 24 } == true)
            }
            val (category, action) = when {
                boot != null -> BOOT_AUTOSTART to boot
                push != null -> PUSH_RECEIVER to push
                system != null -> SYSTEM_BROADCAST to system
                else -> return@mapNotNull null
            }
            receiver.toDeclaration(packageName, ComponentType.RECEIVER, category, action)
        }
        val services = manifest.application.services.mapNotNull { service ->
            val actions = service.intentFilters.flatMap { it.actions }
            val push = actions.firstOrNull(pushActions::contains)
            val sync = actions.firstOrNull(syncActions::contains)
            val (category, action) = when {
                push != null -> PUSH_RECEIVER to push
                service.permission == JOB_PERMISSION -> SCHEDULED_JOB to null
                sync != null -> ACCOUNT_SYNC to sync
                else -> return@mapNotNull null
            }
            service.toDeclaration(packageName, ComponentType.SERVICE, category, action)
        }
        return (receivers + services)
            .sortedWith(compareBy(StaticWakeDeclaration::category, StaticWakeDeclaration::component))
            .distinctBy(StaticWakeDeclaration::component)
    }

    private fun ManifestComponent.toDeclaration(
        packageName: String,
        type: ComponentType,
        category: PotentialWakeCategory,
        action: String?,
    ): StaticWakeDeclaration? {
        val component = qualifiedWakeComponent(packageName, name) ?: return null
        return StaticWakeDeclaration(packageName, component, type, category, action, enabled)
    }

    private const val CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE"
    private const val JOB_PERMISSION = "android.permission.BIND_JOB_SERVICE"

    private val bootActions = setOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.LOCKED_BOOT_COMPLETED",
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        "android.intent.action.REBOOT",
    )

    private val pushActions = setOf(
        "com.google.android.c2dm.intent.RECEIVE",
        "com.google.firebase.MESSAGING_EVENT",
        "com.xiaomi.mipush.RECEIVE_MESSAGE",
        "com.xiaomi.mipush.MESSAGE_ARRIVED",
        "com.xiaomi.push.PING_TIMER",
        "com.huawei.push.action.MESSAGING_EVENT",
        "com.huawei.android.push.intent.RECEIVE",
        "com.huawei.intent.action.PUSH",
        "com.heytap.mcs.action.RECEIVE_MCS_MESSAGE",
        "com.coloros.mcs.action.RECEIVE_MCS_MESSAGE",
        "com.vivo.pushclient.action.RECEIVE",
        "com.meizu.flyme.push.intent.MESSAGE",
    )

    private val syncActions = setOf(
        "android.content.SyncAdapter",
        "android.accounts.AccountAuthenticator",
    )

    private val systemActions = setOf(
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.USER_PRESENT",
        "android.intent.action.ACTION_POWER_CONNECTED",
        "android.intent.action.ACTION_POWER_DISCONNECTED",
        "android.intent.action.PACKAGE_ADDED",
        "android.intent.action.PACKAGE_REPLACED",
        "android.intent.action.PACKAGE_RESTARTED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.LOCALE_CHANGED",
    )
}
