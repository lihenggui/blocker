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
 * Represents the complete AndroidManifest.xml file.
 *
 * This is the root object that contains all manifest information including
 * package details, permissions, and the application with its components.
 */
@Serializable
data class AndroidManifest(
    val packageName: String,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val minSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
    val usesPermissions: List<ManifestPermission> = emptyList(),
    val permissions: List<ManifestPermission> = emptyList(),
    val application: ManifestApplication,
)
