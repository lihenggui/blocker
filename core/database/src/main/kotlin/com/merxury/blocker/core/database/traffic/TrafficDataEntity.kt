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

package com.merxury.blocker.core.database.traffic

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.merxury.blocker.core.model.data.TrafficData

@Entity(tableName = "traffic_data")
data class TrafficDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val ipAddress: String,
    val domain: String? = null,
    val port: Int,
    val path: String? = null,
    val blocked: Boolean = false,
)

fun TrafficDataEntity.asExternalModel() = TrafficData(
    id = id,
    timestamp = timestamp,
    packageName = packageName,
    ipAddress = ipAddress,
    domain = domain,
    port = port,
    path = path,
    blocked = blocked,
)

fun TrafficData.fromExternalModel() = TrafficDataEntity(
    id = id,
    timestamp = timestamp,
    packageName = packageName,
    ipAddress = ipAddress,
    domain = domain,
    port = port,
    path = path,
    blocked = blocked,
)
