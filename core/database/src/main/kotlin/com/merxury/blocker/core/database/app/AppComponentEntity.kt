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

package com.merxury.blocker.core.database.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo

@Entity(
    primaryKeys = ["package_name", "component_name"],
    tableName = "app_component",
    indices = [Index(value = ["package_name", "component_name"])],
)
data class AppComponentEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "ifw_blocked") var ifwBlocked: Boolean,
    @ColumnInfo(name = "pm_blocked") var pmBlocked: Boolean,
    val type: ComponentType,
    val exported: Boolean,
)

fun AppComponentEntity.toComponentInfo() = ComponentInfo(
    packageName = packageName,
    name = componentName,
    type = type,
    exported = exported,
    ifwBlocked = ifwBlocked,
    pmBlocked = pmBlocked,
)

fun ComponentInfo.toAppComponentEntity() = AppComponentEntity(
    packageName = packageName,
    componentName = name,
    type = type,
    exported = exported,
    ifwBlocked = ifwBlocked,
    pmBlocked = pmBlocked,
)
