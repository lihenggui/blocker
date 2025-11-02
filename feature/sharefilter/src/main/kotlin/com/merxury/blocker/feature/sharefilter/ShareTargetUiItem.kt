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

package com.merxury.blocker.feature.sharefilter

import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity

/**
 * UI wrapper for ShareTargetActivityEntity with additional presentation state
 *
 * @param entity the underlying database entity
 * @param isShareableComponent whether this activity qualifies as a shareable component
 * @param isExplicitLaunch whether this activity can be explicitly launched (exported=true)
 * @param isLauncherEntry whether this activity is a launcher entry (has MAIN+LAUNCHER intent filter)
 * @param isDeeplinkEntry whether this activity handles deeplinks (has VIEW+BROWSABLE intent filter with data)
 */
data class ShareTargetUiItem(
    val entity: ShareTargetActivityEntity,
    val isShareableComponent: Boolean = false,
    val isExplicitLaunch: Boolean = false,
    val isLauncherEntry: Boolean = false,
    val isDeeplinkEntry: Boolean = false,
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
fun isShareableComponent(entity: ShareTargetActivityEntity): Boolean = entity.intentFilters.any { filter ->
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
fun isExplicitLaunch(entity: ShareTargetActivityEntity): Boolean = entity.exported

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
fun isLauncherEntry(entity: ShareTargetActivityEntity): Boolean = entity.intentFilters.any { filter ->
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
fun isDeeplinkEntry(entity: ShareTargetActivityEntity): Boolean = entity.intentFilters.any { filter ->
    val hasViewAction = filter.actions.contains("android.intent.action.VIEW")
    val hasDefaultCategory = filter.categories.contains("android.intent.category.DEFAULT")
    val hasBrowsableCategory = filter.categories.contains("android.intent.category.BROWSABLE")
    val hasDataConfig = filter.data.isNotEmpty()

    hasViewAction && hasDefaultCategory && hasBrowsableCategory && hasDataConfig
}
