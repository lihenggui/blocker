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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Success
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TestAppRepository : AppRepository {
    private val appListFlow = MutableSharedFlow<List<InstalledApp>>(replay = 1, onBufferOverflow = DROP_OLDEST)

    override fun getApplicationList(): Flow<List<InstalledApp>> = appListFlow

    override fun updateApplication(packageName: String): Flow<Result<Unit>> = flowOf(Success(Unit))

    override fun updateApplicationList(): Flow<Result<Unit>> = flowOf(Success(Unit))

    override fun searchInstalledApplications(keyword: String): Flow<List<InstalledApp>> = appListFlow.map {
        it.filter { app -> app.packageName.contains(keyword) }
    }

    override fun getApplication(packageName: String): Flow<InstalledApp?> = appListFlow.map {
        it.find { app -> app.packageName == packageName }
    }

    fun sendAppList(appList: List<InstalledApp>) {
        appListFlow.tryEmit(appList)
    }
}
