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
 * Represents an activity component declared in the manifest.
 *
 * Activities provide a screen with which users can interact.
 */
@Serializable
data class ManifestActivity(
    override val name: String,
    override val label: String? = null,
    override val icon: String? = null,
    override val enabled: Boolean = true,
    override val exported: Boolean = false,
    override val permission: String? = null,
    override val process: String? = null,
    override val metaData: List<ManifestMetaData> = emptyList(),
    val intentFilters: List<ManifestIntentFilter> = emptyList(),
    val launchMode: String? = null,
    val screenOrientation: String? = null,
    val theme: String? = null,
    val taskAffinity: String? = null,
) : ManifestComponent()
