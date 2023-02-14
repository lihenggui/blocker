/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

/**
 * [Application] class for Blocker
 */
@HiltAndroidApp
class BlockerApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var blockerNetworkDataSource: BlockerNetworkDataSource

    @Inject
    lateinit var userDataRepository: UserDataRepository

    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
        initServerProvider()
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun initServerProvider() {
        applicationScope.launch {
            userDataRepository.userData
                .map { it.ruleServerProvider }
                .distinctUntilChanged()
                .collect { newProvider ->
                    blockerNetworkDataSource.changeServerProvider(newProvider)
                }
        }
    }
}
