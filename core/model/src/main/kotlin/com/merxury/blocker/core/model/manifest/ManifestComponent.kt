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
 * Base sealed class for all Android manifest components.
 *
 * Represents the four types of application components: activities, services,
 * broadcast receivers, and content providers.
 */
@Serializable
sealed class ManifestComponent {
    abstract val name: String
    abstract val label: String?
    abstract val icon: String?
    abstract val enabled: Boolean
    abstract val exported: Boolean
    abstract val permission: String?
    abstract val process: String?
    abstract val metaData: List<ManifestMetaData>
}
