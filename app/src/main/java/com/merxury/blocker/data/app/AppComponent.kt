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

package com.merxury.blocker.data.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.merxury.blocker.core.entity.EComponentType

@Entity(
    primaryKeys = ["package_name", "component_name"],
    tableName = "app_component",
    indices = [Index(value = ["package_name", "component_name"])],
)
data class AppComponent(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "ifw_blocked") var ifwBlocked: Boolean,
    @ColumnInfo(name = "pm_blocked") var pmBlocked: Boolean,
    val type: EComponentType,
    val exported: Boolean,
)
