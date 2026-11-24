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

package com.merxury.blocker.feature.debloater.impl

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.ManifestActivity
import com.merxury.blocker.core.model.manifest.ManifestComponent
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestProvider
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import com.merxury.blocker.feature.debloater.impl.ComponentClassification as Kind

internal data class ClassifiedComponent(
    val id: ComponentId,
    val displayName: String,
    val className: String,
    val type: ComponentType,
    val kinds: Set<Kind>,
)

/** Uses the base manifest's order and attributes, including its raw exported and permission values. */
internal fun classifyDebloatableComponents(
    packageName: String,
    manifest: AndroidManifest,
    requestedPermissions: List<String>,
): List<ClassifiedComponent> {
    val application = manifest.application
    val activities = application.activities.filter { activity ->
        activity.intentFilters.any { filter ->
            filter.actions.any { it in candidateActions } || BROWSABLE in filter.categories
        }
    }
    val hasWakelockPermission = requestedPermissions.any { it in wakelockPermissions }
    return (activities + application.services + application.receivers + application.providers).map { component ->
        val name = if (component.name.startsWith(".")) manifest.packageName + component.name else component.name
        // PackageManager expands a bare name as well. Keep the original name for display and search.
        val id = ComponentId(packageName, if ('.' in name) name else "${manifest.packageName}.$name")
        val type = when (component) {
            is ManifestActivity -> ComponentType.ACTIVITY
            is ManifestService -> ComponentType.SERVICE
            is ManifestReceiver -> ComponentType.RECEIVER
            is ManifestProvider -> ComponentType.PROVIDER
        }
        ClassifiedComponent(id, component.label ?: name.substringAfterLast('.'), name, type, component.kinds(name, hasWakelockPermission))
    }.distinctBy { it.id }
}

private fun ManifestComponent.kinds(name: String, hasWakelockPermission: Boolean): Set<Kind> = buildSet {
    val filters = when (val component = this@kinds) {
        is ManifestActivity -> component.intentFilters
        is ManifestService -> component.intentFilters
        is ManifestReceiver -> component.intentFilters
        is ManifestProvider -> emptyList()
    }
    if (filters.any(ManifestIntentFilter::isShareable)) add(Kind.SHAREABLE)
    if (filters.any { MAIN in it.actions && LAUNCHER in it.categories }) add(Kind.LAUNCHER)
    if (filters.any(ManifestIntentFilter::isDeepLink)) add(Kind.DEEPLINK)
    if (hasWakelockPermission) add(Kind.WAKELOCK)
    if (filters.any { filter -> filter.actions.any { it in autoStartActions } }) add(Kind.AUTO_START)
    if (exported && permission == null) add(Kind.EXPORTED_NO_PERM)
    if (this@kinds is ManifestService && foregroundServiceType != null) add(Kind.FOREGROUND_SERVICE)
    if (name.lowercase().let { "messagingservice" in it || "pushservice" in it }) add(Kind.PUSH_SERVICE)
    if (this@kinds is ManifestProvider && exported && grantUriPermissions) add(Kind.DANGEROUS_PROVIDER)
}

private fun ManifestIntentFilter.isShareable() = (SEND in actions || SEND_MULTIPLE in actions) && DEFAULT in categories && data.any { it.mimeType != null }

private fun ManifestIntentFilter.isDeepLink() = VIEW in actions && DEFAULT in categories && BROWSABLE in categories && data.isNotEmpty()

private const val VIEW = "android.intent.action.VIEW"
private const val SEND = "android.intent.action.SEND"
private const val SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
private const val MAIN = "android.intent.action.MAIN"
private const val LAUNCHER = "android.intent.category.LAUNCHER"
private const val DEFAULT = "android.intent.category.DEFAULT"
private const val BROWSABLE = "android.intent.category.BROWSABLE"
private val candidateActions = setOf(VIEW, SEND, SEND_MULTIPLE)
private val wakelockPermissions = setOf(
    "android.permission.WAKE_LOCK",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_EXACT_ALARM",
)
private val autoStartActions = setOf(
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.LOCKED_BOOT_COMPLETED",
    "android.intent.action.MY_PACKAGE_REPLACED",
    "android.intent.action.USER_PRESENT",
)
