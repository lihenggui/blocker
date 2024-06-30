/*
 * Copyright 2024 Blocker
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

import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.serialization.Serializable

/**
 * Network representation for the component data stored in the server
 */
@Serializable
data class NetworkComponentDetail(
    val name: String,
    val sdkName: String? = null,
    val description: String? = null,
    val disableEffect: String? = null,
    val contributor: String? = null,
    val addedVersion: String? = null,
    val recommendToBlock: Boolean = false,
)

fun NetworkComponentDetail.asExternalModel(): ComponentDetail = ComponentDetail(
    name = name,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    contributor = contributor,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock,
)

fun ComponentDetail.asNetworkModel(): NetworkComponentDetail = NetworkComponentDetail(
    name = name,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    contributor = contributor,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock,
)
