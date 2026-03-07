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

package com.merxury.blocker.core.di

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.AssetManager
import com.merxury.blocker.core.utils.AppDebugChecker
import com.merxury.blocker.core.utils.PackageInfoDataSource
import com.merxury.blocker.core.utils.PmAppDebugChecker
import com.merxury.blocker.core.utils.PmPackageInfoDataSource
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import com.merxury.blocker.core.utils.ShellRootAvailabilityChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SysModule {
    @Provides
    fun providePackageManager(
        app: Application,
    ): PackageManager = app.packageManager

    @Singleton
    @Provides
    fun provideRootAvailabilityChecker(
        impl: ShellRootAvailabilityChecker,
    ): RootAvailabilityChecker = impl

    @Singleton
    @Provides
    fun providePackageInfoDataSource(
        impl: PmPackageInfoDataSource,
    ): PackageInfoDataSource = impl

    @Singleton
    @Provides
    fun provideAppDebugChecker(
        impl: PmAppDebugChecker,
    ): AppDebugChecker = impl

    @Singleton
    @Provides
    @AppPackageName
    fun provideAppPackageName(
        app: Application,
    ): String = app.packageName

    @Provides
    @FilesDir
    fun provideFilesDir(
        app: Application,
    ): File = app.filesDir

    @Provides
    @CacheDir
    fun provideCacheDir(
        app: Application,
    ): File = app.cacheDir

    @Provides
    fun provideAssetManager(
        app: Application,
    ): AssetManager = app.assets
}
