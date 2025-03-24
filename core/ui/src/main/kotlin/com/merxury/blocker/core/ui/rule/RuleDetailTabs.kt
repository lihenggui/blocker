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

package com.merxury.blocker.core.ui.rule

import androidx.annotation.VisibleForTesting
import com.merxury.blocker.core.ui.R

sealed class RuleDetailTabs(val name: String, val title: Int = 0) {
    object Description : RuleDetailTabs(DESCRIPTION, title = R.string.core_ui_description)
    object Applicable : RuleDetailTabs(APPLICABLE, title = R.string.core_ui_applicable_application)

    override fun toString(): String = "Screen name = $name"

    companion object {
        const val DESCRIPTION = "description"
        @VisibleForTesting
        const val APPLICABLE = "applicable_app"

        fun fromName(name: String?): RuleDetailTabs = when (name) {
            DESCRIPTION -> Description
            APPLICABLE -> Applicable
            else -> throw IllegalArgumentException("Invalid screen name: $name in rule detail page")
        }
    }
}
