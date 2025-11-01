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
import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityDao
import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Success
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

internal class LocalShareTargetRepository @Inject constructor(
    private val localDataSource: LocalShareTargetDataSource,
    private val cacheDataSource: CacheShareTargetDataSource,
    private val shareTargetActivityDao: ShareTargetActivityDao,
    private val userDataRepository: UserDataRepository,
    private val pm: PackageManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ShareTargetRepository {

    override fun getShareTargetActivities(): Flow<List<ShareTargetActivityEntity>> = cacheDataSource.getShareTargetActivities()
        .onStart {
            val latestActivities = localDataSource.getShareTargetActivities().first()
            shareTargetActivityDao.upsertAll(latestActivities)
        }
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

    override fun updateShareTargetActivities(): Flow<Result<Unit>> = flow {
        val latestActivities = localDataSource.getShareTargetActivities().first()
        shareTargetActivityDao.upsertAll(latestActivities)
        emit(Success(Unit))
    }.flowOn(ioDispatcher)
}
