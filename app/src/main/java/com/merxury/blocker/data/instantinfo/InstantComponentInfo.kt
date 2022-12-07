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

package com.merxury.blocker.data.instantinfo

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity

@Keep
@Entity(primaryKeys = ["package_path", "component_name"], tableName = "instant_component_info")
data class InstantComponentInfo(
    @ColumnInfo(name = "package_path") val packagePath: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "recommend_to_block") val recommendToBlock: Boolean
)
