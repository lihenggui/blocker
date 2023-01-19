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

package com.merxury.blocker.core.model.data

import kotlinx.datetime.Instant

/**
 * External data layer representation of the installed app in the device
 */
data class InstalledApp(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val firstInstallTime: Instant? = null,
    val lastUpdateTime: Instant? = null,
    val isEnabled: Boolean = true,
    val isSystem: Boolean = false,
    val label: String = "",
)
