/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.database.app

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppRepository @Inject constructor(
    private val installedAppDao: InstalledAppDao
) {
    suspend fun getInstalledApp() = installedAppDao.getAll()

    suspend fun getInstalledAppCount() = installedAppDao.getCount()

    suspend fun getByPackageName(packageName: String) =
        installedAppDao.getByPackageName(packageName)

    suspend fun addInstalledAppList(list: Array<InstalledAppEntity>) {
        installedAppDao.insertAll(*list)
    }

    suspend fun addInstalledApp(app: InstalledAppEntity) {
        installedAppDao.insert(app)
    }

    suspend fun deleteAll() {
        installedAppDao.deleteAll()
    }
}
