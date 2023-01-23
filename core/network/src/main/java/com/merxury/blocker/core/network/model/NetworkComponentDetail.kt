/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.network.model

import kotlinx.serialization.Serializable

/**
 * Network representation for the component data stored in the server
 */
@Serializable
data class NetworkComponentDetail(
    val fullName: String,
    val simpleName: String,
    val packageName: String,
    val icon: String? = null,
    val sdkName: String? = null,
    val description: String? = null,
    val disableEffect: String? = null,
    val author: String? = null,
    val addedVersion: String? = null,
    val recommendToBlock: Boolean = false,
)
