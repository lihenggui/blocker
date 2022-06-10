package com.merxury.blocker.data.app

import com.merxury.libkit.entity.EComponentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppComponentRepository @Inject constructor(private val appComponentDao: AppComponentDao) {
    suspend fun getAppComponent(packageName: String): List<AppComponent> {
        return appComponentDao.getByPackageName(packageName)
    }

    suspend fun getAppComponentByType(packageName: String, type: EComponentType): List<AppComponent> {
        return appComponentDao.getByPackageNameAndType(packageName, type)
    }

    suspend fun getAppComponentByName(keywords: List<String>): List<AppComponent> {
        return appComponentDao.getByName(keywords)
    }

    suspend fun addAppComponents(vararg appComponents: AppComponent) {
        appComponentDao.insert(*appComponents)
    }
}