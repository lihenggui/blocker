package com.merxury.blocker.data.app

import com.merxury.libkit.entity.EComponentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppComponentRepository @Inject constructor(private val appComponentDao: AppComponentDao) {
    suspend fun getAppComponents(packageName: String): List<AppComponent> {
        return appComponentDao.getByPackageName(packageName)
    }

    suspend fun getAppComponent(packageName: String, componentName: String): AppComponent? {
        return appComponentDao.getByPackageNameAndComponentName(packageName, componentName)
    }

    suspend fun getAppComponentByType(packageName: String, type: EComponentType): List<AppComponent> {
        return appComponentDao.getByPackageNameAndType(packageName, type)
    }

    suspend fun getAppComponentByName(keyword: String): List<AppComponent> {
        return appComponentDao.getByName(keyword)
    }

    suspend fun addAppComponents(vararg appComponents: AppComponent) {
        appComponentDao.insert(*appComponents)
    }

    suspend fun deleteAll() {
        appComponentDao.deleteAll()
    }
}