/*
 * Copyright 2026 Blocker
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
package com.merxury.blocker.core.database.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.OperationEntry

@Entity(
    tableName = "operation_entry",
    primaryKeys = ["operation_id", "package_name", "component_name"],
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OperationEntryEntity(
    @ColumnInfo(name = "operation_id") val operationId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "component_type") val componentType: ComponentType,
    @ColumnInfo(name = "before_pm") val beforePm: Boolean,
    @ColumnInfo(name = "before_ifw") val beforeIfw: Boolean,
    @ColumnInfo(name = "after_pm") val afterPm: Boolean,
    @ColumnInfo(name = "after_ifw") val afterIfw: Boolean,
)

fun OperationEntryEntity.asExternalModel() = OperationEntry(
    operationId = operationId,
    packageName = packageName,
    componentName = componentName,
    componentType = componentType,
    beforePmBlocked = beforePm,
    beforeIfwBlocked = beforeIfw,
    afterPmBlocked = afterPm,
    afterIfwBlocked = afterIfw,
)

fun OperationEntry.asEntity() = OperationEntryEntity(
    operationId = operationId,
    packageName = packageName,
    componentName = componentName,
    componentType = componentType,
    beforePm = beforePmBlocked,
    beforeIfw = beforeIfwBlocked,
    afterPm = afterPmBlocked,
    afterIfw = afterIfwBlocked,
)
