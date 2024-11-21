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

package com.merxury.blocker.core.data.respository.traffic

import com.merxury.blocker.core.database.traffic.TrafficDataDao
import com.merxury.blocker.core.database.traffic.asExternalModel
import com.merxury.blocker.core.database.traffic.fromExternalModel
import com.merxury.blocker.core.model.data.TrafficData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalTrafficDataRepository @Inject constructor(
    private val trafficDataDao: TrafficDataDao,
) : TrafficDataRepository {
    override fun insertTrafficData(trafficData: TrafficData) {
        trafficDataDao.insert(trafficData.fromExternalModel())
    }

    override fun getTrafficData(packageName: String, keyword: String): Flow<List<TrafficData>> = trafficDataDao.getTrafficData(packageName, keyword)
        .map { trafficDataList ->
            trafficDataList.map { it.asExternalModel() }
        }

    override fun deleteTrafficData() {
        trafficDataDao.deleteAll()
    }
}
