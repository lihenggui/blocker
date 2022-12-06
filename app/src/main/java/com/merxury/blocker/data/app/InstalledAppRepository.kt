package com.merxury.blocker.data.app

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

    suspend fun addInstalledAppList(list: Array<InstalledApp>) {
        installedAppDao.insertAll(*list)
    }

    suspend fun addInstalledApp(app: InstalledApp) {
        installedAppDao.insert(app)
    }

    suspend fun deleteAll() {
        installedAppDao.deleteAll()
    }
}
