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
 * Represents the application element in the manifest.
 *
 * Contains all components and application-level configuration.
 */
@Serializable
data class ManifestApplication(
    val label: String? = null,
    val icon: String? = null,
    val theme: String? = null,
    val debuggable: Boolean = false,
    val allowBackup: Boolean = true,
    val activities: List<ManifestActivity> = emptyList(),
    val services: List<ManifestService> = emptyList(),
    val receivers: List<ManifestReceiver> = emptyList(),
    val providers: List<ManifestProvider> = emptyList(),
    val metaData: List<ManifestMetaData> = emptyList(),
)
