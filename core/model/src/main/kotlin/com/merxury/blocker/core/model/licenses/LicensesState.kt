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

package com.merxury.blocker.core.model.licenses

import kotlinx.serialization.Serializable

@Serializable
data class LicenseItem(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val spdxLicenses: List<SpdxLicense>?,
    val name: String?,
    val scm: Scm?,
)

@Serializable
data class SpdxLicense(
    val identifier: String,
    val name: String,
    val url: String,
)

@Serializable
data class Scm(
    val url: String,
)
