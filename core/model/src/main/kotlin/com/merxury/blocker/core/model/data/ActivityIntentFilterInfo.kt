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

package com.merxury.blocker.core.model.data

import kotlinx.serialization.Serializable

/**
 * Represents an Activity with its intent filters
 *
 * @param name the activity name (android:name attribute)
 * @param packageName the package name containing this activity
 * @param exported whether the activity is exported (android:exported attribute)
 * @param label the activity label, with @string resource resolution attempted
 * @param intentFilters list of intent filters defined for this activity
 */
@Serializable
data class ActivityIntentFilterInfo(
    val name: String,
    val packageName: String,
    val exported: Boolean,
    val label: String?,
    val intentFilters: List<IntentFilterInfo>,
)
