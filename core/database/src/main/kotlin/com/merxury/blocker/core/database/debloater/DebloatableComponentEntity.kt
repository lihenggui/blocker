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

package com.merxury.blocker.core.database.debloater

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.TypeConverters
import com.merxury.blocker.core.database.util.IntentFilterInfoConverter
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.IntentFilterInfo

@Entity(
    primaryKeys = ["package_name", "component_name"],
    tableName = "debloatable_component",
    indices = [Index(value = ["package_name", "component_name"])],
)
@TypeConverters(IntentFilterInfoConverter::class)
data class DebloatableComponentEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "simple_name") val simpleName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "ifw_blocked") var ifwBlocked: Boolean,
    @ColumnInfo(name = "pm_blocked") var pmBlocked: Boolean,
    @ColumnInfo(name = "type") val type: ComponentType,
    @ColumnInfo(name = "exported") val exported: Boolean,
    @ColumnInfo(name = "label") val label: String? = null,
    @ColumnInfo(name = "intent_filters") val intentFilters: List<IntentFilterInfo> = emptyList(),
) {
    fun toComponentInfo() = ComponentInfo(
        packageName = packageName,
        name = componentName,
        simpleName = simpleName,
        type = type,
        exported = exported,
        ifwBlocked = ifwBlocked,
        pmBlocked = pmBlocked,
    )
}
