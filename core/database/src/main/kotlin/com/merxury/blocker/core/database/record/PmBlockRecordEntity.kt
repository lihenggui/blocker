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

package com.merxury.blocker.core.database.record

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A component Blocker disabled through PackageManager for one user. The system does not record
 * who disabled a component, so this is the only way to tell Blocker's blocks from those of the
 * app itself or of the system.
 *
 * @property recordedAt when the block was first recorded, in epoch milliseconds; kept for support
 */
@Entity(
    tableName = "pm_block_record",
    primaryKeys = ["package_name", "class_name", "user_id"],
)
data class PmBlockRecordEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "class_name") val className: String,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
)
