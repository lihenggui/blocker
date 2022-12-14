/*
 * Copyright 2022 Blocker
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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Network representation for the component data stored in the server
 */
@Serializable
data class NetworkComponentDetail(
    val name: String,
    val icon: String? = null,
    @SerialName("sdk_name")
    val sdkName: String? = null,
    val description: String? = null,
    @SerialName("disable_effect")
    val disableEffect: String? = null,
    val author: String? = null,
    @SerialName("added_version")
    val addedVersion: String? = null,
    @SerialName("recommend_to_block")
    val recommendToBlock: Boolean = false,
)
