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

package com.merxury.blocker.core.model.data

import kotlinx.serialization.Serializable

/**
 * Represents intent filter data attributes from <data /> tag
 *
 * @param scheme the URI scheme (android:scheme attribute)
 * @param host the URI host (android:host attribute)
 * @param port the URI port (android:port attribute)
 * @param path the URI path (android:path attribute)
 * @param pathPrefix the URI path prefix (android:pathPrefix attribute)
 * @param pathPattern the URI path pattern (android:pathPattern attribute)
 * @param mimeType the MIME type (android:mimeType attribute)
 */
@Serializable
data class IntentFilterDataInfo(
    val scheme: String? = null,
    val host: String? = null,
    val port: String? = null,
    val path: String? = null,
    val pathPrefix: String? = null,
    val pathPattern: String? = null,
    val mimeType: String? = null,
)
