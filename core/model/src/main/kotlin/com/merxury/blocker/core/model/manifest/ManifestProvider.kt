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
 * Represents a content provider component declared in the manifest.
 *
 * Providers manage access to a structured set of data.
 */
@Serializable
data class ManifestProvider(
    override val name: String,
    override val label: String? = null,
    override val icon: String? = null,
    override val enabled: Boolean = true,
    override val exported: Boolean = false,
    override val permission: String? = null,
    override val process: String? = null,
    override val metaData: List<ManifestMetaData> = emptyList(),
    val authorities: String? = null,
    val grantUriPermissions: Boolean = false,
    val readPermission: String? = null,
    val writePermission: String? = null,
) : ManifestComponent()
