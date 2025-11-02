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
 */
data class ShareTargetUiItem(
    val entity: ShareTargetActivityEntity,
    val isShareableComponent: Boolean = false,
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
fun isShareableComponent(entity: ShareTargetActivityEntity): Boolean {
    return entity.intentFilters.any { filter ->
        val hasSendAction = filter.actions.any {
            it == "android.intent.action.SEND" || it == "android.intent.action.SEND_MULTIPLE"
        }
        val hasDefaultCategory = filter.categories.contains("android.intent.category.DEFAULT")
        val hasMimeType = filter.data.any { it.mimeType != null }

        hasSendAction && hasDefaultCategory && hasMimeType
    }
}
