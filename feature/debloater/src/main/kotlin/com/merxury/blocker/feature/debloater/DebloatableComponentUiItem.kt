/*
 * Copyright 2025 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.feature.debloater

import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity

/**
 * UI wrapper for DebloatableComponentEntity with additional presentation state
 *
 * @param entity the underlying database entity
 * @param isShareableComponent whether this activity qualifies as a shareable component
 * @param isExplicitLaunch whether this activity can be explicitly launched (exported=true)
 * @param isLauncherEntry whether this activity is a launcher entry (has MAIN+LAUNCHER intent filter)
 * @param isDeeplinkEntry whether this activity handles deeplinks (has VIEW+BROWSABLE intent filter with data)
 * @param isWakelockComponent whether this component is related to wakelock functionality
 * @param isAutoStartReceiver whether this receiver has auto-start capabilities
 * @param isExportedNoPerm whether this component is exported without permission protection
 * @param isForegroundService whether this service declares foreground service type
 * @param isSystemService whether this is a system-level service
 * @param isPushService whether this service is a push notification service
 * @param isDangerousProvider whether this provider has potentially dangerous permissions
 * @param isInitProvider whether this provider is an initialization provider
 */
data class DebloatableComponentUiItem(
    val entity: DebloatableComponentEntity,
    val isShareableComponent: Boolean = false,
    val isExplicitLaunch: Boolean = false,
    val isLauncherEntry: Boolean = false,
    val isDeeplinkEntry: Boolean = false,
    val isWakelockComponent: Boolean = false,
    val isAutoStartReceiver: Boolean = false,
    val isExportedNoPerm: Boolean = false,
    val isForegroundService: Boolean = false,
    val isSystemService: Boolean = false,
    val isPushService: Boolean = false,
    val isDangerousProvider: Boolean = false,
    val isInitProvider: Boolean = false,
)

/**
 * Determines if an activity qualifies as a shareable component based on its intent filters.
 *
 * A shareable component must have an intent filter with:
 * - action: android.intent.action.SEND (for single item) or android.intent.action.SEND_MULTIPLE (for multiple items)
 * - category: android.intent.category.DEFAULT
 * - data: at least one mimeType specified
 *
 * @param entity the activity entity to check
 * @return true if the activity is a shareable component, false otherwise
 */
fun isShareableComponent(entity: DebloatableComponentEntity): Boolean = entity.intentFilters.any { filter ->
    val hasSendAction = filter.actions.any {
        it == "android.intent.action.SEND" || it == "android.intent.action.SEND_MULTIPLE"
    }
    val hasDefaultCategory = filter.categories.contains("android.intent.category.DEFAULT")
    val hasMimeType = filter.data.any { it.mimeType != null }

    hasSendAction && hasDefaultCategory && hasMimeType
}

/**
 * Determines if an activity can be explicitly launched via setComponent.
 *
 * An explicitly launchable activity must have:
 * - exported: true
 *
 * @param entity the activity entity to check
 * @return true if the activity is exported, false otherwise
 */
fun isExplicitLaunch(entity: DebloatableComponentEntity): Boolean = entity.exported

/**
 * Determines if an activity is a launcher entry (can be launched from home screen).
 *
 * A launcher entry must have an intent filter with:
 * - action: android.intent.action.MAIN
 * - category: android.intent.category.LAUNCHER
 *
 * @param entity the activity entity to check
 * @return true if the activity is a launcher entry, false otherwise
 */
fun isLauncherEntry(entity: DebloatableComponentEntity): Boolean = entity.intentFilters.any { filter ->
    val hasMainAction = filter.actions.contains("android.intent.action.MAIN")
    val hasLauncherCategory = filter.categories.contains("android.intent.category.LAUNCHER")
    hasMainAction && hasLauncherCategory
}

/**
 * Determines if an activity handles deeplinks (custom scheme or App Links).
 *
 * A deeplink handler must have an intent filter with:
 * - action: android.intent.action.VIEW
 * - category: android.intent.category.DEFAULT
 * - category: android.intent.category.BROWSABLE
 * - data: at least one data element with scheme/host configuration
 *
 * @param entity the activity entity to check
 * @return true if the activity handles deeplinks, false otherwise
 */
fun isDeeplinkEntry(entity: DebloatableComponentEntity): Boolean = entity.intentFilters.any { filter ->
    val hasViewAction = filter.actions.contains("android.intent.action.VIEW")
    val hasDefaultCategory = filter.categories.contains("android.intent.category.DEFAULT")
    val hasBrowsableCategory = filter.categories.contains("android.intent.category.BROWSABLE")
    val hasDataConfig = filter.data.isNotEmpty()

    hasViewAction && hasDefaultCategory && hasBrowsableCategory && hasDataConfig
}

/**
 * Determines if a component is related to wakelock functionality.
 *
 * This checks if the component's package uses wakelock or alarm permissions:
 * - android.permission.WAKE_LOCK
 * - android.permission.SCHEDULE_EXACT_ALARM
 * - android.permission.USE_EXACT_ALARM
 *
 * @param entity the component entity to check
 * @param appPermissions list of permissions declared by the app
 * @return true if the app uses wakelock-related permissions, false otherwise
 */
fun isWakelockComponent(entity: DebloatableComponentEntity, appPermissions: List<String>): Boolean = appPermissions.any {
    it == "android.permission.WAKE_LOCK" ||
        it == "android.permission.SCHEDULE_EXACT_ALARM" ||
        it == "android.permission.USE_EXACT_ALARM"
}

/**
 * Determines if a receiver has auto-start capabilities.
 *
 * Auto-start receivers listen for system boot or package replacement events:
 * - android.intent.action.BOOT_COMPLETED
 * - android.intent.action.LOCKED_BOOT_COMPLETED
 * - android.intent.action.MY_PACKAGE_REPLACED
 * - android.intent.action.USER_PRESENT
 *
 * @param entity the receiver entity to check
 * @return true if the receiver has auto-start intent filters, false otherwise
 */
fun isAutoStartReceiver(entity: DebloatableComponentEntity): Boolean = entity.intentFilters.any { filter ->
    filter.actions.any {
        it == "android.intent.action.BOOT_COMPLETED" ||
            it == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            it == "android.intent.action.MY_PACKAGE_REPLACED" ||
            it == "android.intent.action.USER_PRESENT"
    }
}

/**
 * Determines if a component is exported without permission protection.
 *
 * Such components can be accessed by any app, potentially posing security risks.
 *
 * @param entity the component entity to check
 * @return true if the component is exported without requiring a permission, false otherwise
 */
fun isExportedNoPerm(entity: DebloatableComponentEntity): Boolean = entity.exported && entity.permission == null

/**
 * Determines if a service declares a foreground service type.
 *
 * Foreground services must declare their type starting from Android 10 (API 29).
 *
 * @param entity the service entity to check
 * @return true if the service has a foreground service type declared, false otherwise
 */
fun isForegroundService(entity: DebloatableComponentEntity): Boolean = entity.foregroundServiceType != null

/**
 * Determines if a service or receiver is a system-level component.
 *
 * System components include:
 * - NotificationListenerService
 * - AccessibilityService
 * - VpnService
 * - CompanionDeviceService
 * - DeviceAdminReceiver
 * - JobService
 *
 * @param entity the service or receiver entity to check
 * @return true if the component is a system service type, false otherwise
 */
fun isSystemService(entity: DebloatableComponentEntity): Boolean {
    val name = entity.componentName.lowercase()
    return name.contains("notificationlistenerservice") ||
        name.contains("accessibilityservice") ||
        name.contains("vpnservice") ||
        name.contains("companiondeviceservice") ||
        name.contains("deviceadminreceiver") ||
        name.contains("jobservice") ||
        entity.intentFilters.any { filter ->
            filter.actions.any {
                it == "android.service.notification.NotificationListenerService" ||
                    it == "android.accessibilityservice.AccessibilityService" ||
                    it == "android.net.VpnService" ||
                    it == "android.app.action.DEVICE_ADMIN_ENABLED"
            }
        }
}

/**
 * Determines if a service is a push notification service.
 *
 * Common push services include:
 * - Firebase Cloud Messaging (FCM)
 * - Xiaomi Push
 * - Huawei Push
 * - Oppo Push
 * - Vivo Push
 * - Meizu Push
 *
 * @param entity the service entity to check
 * @return true if the service is a push notification service, false otherwise
 */
fun isPushService(entity: DebloatableComponentEntity): Boolean {
    val name = entity.componentName.lowercase()
    return name.contains("firebasemessaging") ||
        name.contains("fcm") ||
        name.contains("xiaomipush") ||
        name.contains("mipush") ||
        name.contains("huaweipush") ||
        name.contains("oppopush") ||
        name.contains("vivopush") ||
        name.contains("meizupush") ||
        entity.intentFilters.any { filter ->
            filter.actions.any {
                it == "com.google.firebase.MESSAGING_EVENT" ||
                    it.contains("xiaomi.mipush") ||
                    it.contains("huawei.push") ||
                    it.contains("oppo.push") ||
                    it.contains("vivo.push")
            }
        }
}

/**
 * Determines if a provider has potentially dangerous permission grants.
 *
 * Dangerous providers are exported and grant URI permissions,
 * allowing other apps to access their content with elevated permissions.
 *
 * @param entity the provider entity to check
 * @return true if the provider is exported with URI permission grants, false otherwise
 */
fun isDangerousProvider(entity: DebloatableComponentEntity): Boolean = entity.exported && entity.grantUriPermissions

/**
 * Determines if a provider is an initialization provider.
 *
 * Initialization providers run before the application's onCreate() and are used
 * for automatic library initialization:
 * - androidx.startup.InitializationProvider
 * - androidx.work.impl.WorkManagerInitializer
 *
 * @param entity the provider entity to check
 * @return true if the provider is an initialization provider, false otherwise
 */
fun isInitProvider(entity: DebloatableComponentEntity): Boolean {
    val name = entity.componentName.lowercase()
    return name.contains("initializationprovider") ||
        name.contains("workmanagerinitializer") ||
        name.contains("androidx.startup")
}
