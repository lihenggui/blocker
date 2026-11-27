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
import androidx.room.Index
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.history.OperationEntry

@Entity(
    tableName = "operation_entry",
    primaryKeys = ["operation_id", "user_id", "package_name", "component_name"],
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("operation_id")],
)
data class OperationEntryEntity(
    @ColumnInfo(name = "operation_id") val operationId: Long,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "component_type") val componentType: ComponentType,
    @ColumnInfo(name = "before_pm") val beforePm: PmSetting,
    @ColumnInfo(name = "after_pm") val afterPm: PmSetting,
    @ColumnInfo(name = "before_ifw") val beforeIfw: IfwBlock,
    @ColumnInfo(name = "after_ifw") val afterIfw: IfwBlock,
)

fun OperationEntryEntity.asExternalModel() = OperationEntry(
    operationId = operationId,
    key = ComponentKey(ComponentId(packageName, componentName), UserId(userId)),
    type = componentType,
    before = ControlState(pm = beforePm, ifw = beforeIfw),
    after = ControlState(pm = afterPm, ifw = afterIfw),
)

fun OperationEntry.asEntity() = OperationEntryEntity(
    operationId = operationId,
    userId = key.userId.value,
    packageName = key.id.packageName,
    componentName = key.id.className,
    componentType = type,
    beforePm = before.pm,
    afterPm = after.pm,
    beforeIfw = before.ifw,
    afterIfw = after.ifw,
)
