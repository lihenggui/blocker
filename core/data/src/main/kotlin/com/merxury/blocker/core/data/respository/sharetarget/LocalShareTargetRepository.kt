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

package com.merxury.blocker.core.data.respository.sharetarget

import android.content.pm.PackageManager
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class LocalShareTargetRepository @Inject constructor(
    private val localDataSource: LocalShareTargetDataSource,
    private val userDataRepository: UserDataRepository,
    private val pm: PackageManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ShareTargetRepository {

    override fun getShareTargetActivities(): Flow<List<ComponentInfo>> =
        localDataSource.getShareTargetActivities()
            .combine(userDataRepository.userData) { activities, userData ->
                if (userData.showSystemApps) {
                    activities
                } else {
                    activities.filter { activity ->
                        !ApplicationUtil.isSystemApp(pm, activity.packageName)
                    }
                }
            }
            .flowOn(ioDispatcher)
}
