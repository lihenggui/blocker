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

package com.merxury.blocker.core.model.manifest

import kotlinx.serialization.Serializable

/**
 * Represents an intent filter that specifies the types of intents a component can respond to.
 *
 * Intent filters contain actions, categories, and data specifications that define
 * what intents the component can handle.
 */
@Serializable
data class ManifestIntentFilter(
    val actions: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val data: List<IntentFilterData> = emptyList(),
    val priority: Int? = null,
    val autoVerify: Boolean = false,
)
