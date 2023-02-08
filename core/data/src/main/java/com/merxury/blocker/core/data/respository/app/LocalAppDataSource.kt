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

package com.merxury.blocker.core.data.respository.app

import android.content.pm.PackageManager
import com.merxury.blocker.core.data.di.AppPackageName
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.getInstalledPackagesCompat
import com.merxury.blocker.core.extension.getVersionCode
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.minSdkVersionCompat
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAppDataSource @Inject constructor(
    @AppPackageName private val appPackageName: String,
    private val pm: PackageManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AppDataSource {
    override fun getApplicationList(): Flow<List<InstalledApp>> = flow {
        val list = pm.getInstalledPackagesCompat(0)
            .map {
                InstalledApp(
                    packageName = it.packageName,
                    versionName = it.versionName.orEmpty(),
                    versionCode = it.getVersionCode(),
                    minSdkVersion = it.applicationInfo.minSdkVersionCompat(),
                    targetSdkVersion = it.applicationInfo?.targetSdkVersion ?: 0,
                    firstInstallTime = Instant.fromEpochMilliseconds(it.firstInstallTime),
                    lastUpdateTime = Instant.fromEpochMilliseconds(it.lastUpdateTime),
                    isEnabled = it.applicationInfo?.enabled ?: false,
                    isSystem = ApplicationUtil.isSystemApp(pm, it.packageName),
                    label = it.applicationInfo?.loadLabel(pm).toString(),
                )
            }
            .filterNot { it.packageName == appPackageName }
        emit(list)
    }
        .flowOn(ioDispatcher)
}
