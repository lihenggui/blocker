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

package com.merxury.blocker.core.database.cmpdetail

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.merxury.blocker.core.model.data.ComponentDetail
import javax.annotation.Nonnull

@Entity(tableName = "component_detail")
data class ComponentDetailEntity(
    @Nonnull
    @PrimaryKey
    val name: String,
    @ColumnInfo(name = "sdk_name")
    val sdkName: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "disable_effect")
    val disableEffect: String? = null,
    val contributor: String? = null,
    @ColumnInfo(name = "added_version")
    val addedVersion: String? = null,
    @ColumnInfo(name = "recommend_to_block")
    val recommendToBlock: Boolean = false,
)

fun ComponentDetailEntity.asExternalModel() = ComponentDetail(
    name = name,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    contributor = contributor,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock,
)

fun ComponentDetail.toEntity() = ComponentDetailEntity(
    name = name,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    contributor = contributor,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock,
)
