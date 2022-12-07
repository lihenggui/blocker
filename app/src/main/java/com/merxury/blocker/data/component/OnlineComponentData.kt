/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.data.component

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class OnlineComponentData(
    var name: String? = null,
    var icon: String? = null,
    @SerializedName("sdk_name")
    var sdkName: String? = null,
    var description: String? = null,
    @SerializedName("disableEffect")
    var disableEffect: String? = null,
    var author: String? = null,
    @SerializedName("added_version")
    var addedVersion: String? = null,
    @SerializedName("recommend_to_block")
    var recommendToBlock: Boolean = false,
)
