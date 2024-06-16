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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(trafficData: TrafficDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(trafficData: List<TrafficDataEntity>)

    @Query("SELECT * FROM traffic_data WHERE packageName = :packageName AND (domain LIKE '%' || :keyword || '%' OR path LIKE '%' || :keyword || '%') ORDER BY timestamp DESC")
    fun getTrafficData(packageName: String, keyword: String): Flow<List<TrafficDataEntity>>

    @Query("DELETE FROM traffic_data")
    fun deleteAll()
}
