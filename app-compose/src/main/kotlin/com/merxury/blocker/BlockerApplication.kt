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
import androidx.work.ExistingWorkPolicy.REPLACE
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.core.rule.work.CopyRulesToStorageWorker
import com.merxury.blocker.sync.initializers.Sync
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
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
    lateinit var userDataRepository: UserDataRepository

    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Sync.initialize(context = this)
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
        initServerProvider()
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    private fun initServerProvider() {
        applicationScope.launch {
            val userData = userDataRepository.userData.first()
            if (!userData.isFirstTimeInitializationCompleted) {
                setDefaultRuleProvider()
                copyRulesToInternalStorage()
                userDataRepository.setIsFirstTimeInitializationCompleted(true)
            }
        }
    }

    private fun copyRulesToInternalStorage() {
        WorkManager.getInstance(this@BlockerApplication)
            .enqueueUniqueWork(
                "CopyRuleToInternalStorage",
                REPLACE,
                CopyRulesToStorageWorker.copyWork(),
            )
    }

    private suspend fun setDefaultRuleProvider() {
        // Set default server provider for first time run
        val locale = Locale.getDefault().toString()
        if (locale == "zh_CN") {
            // Set default server provider to GitLab for Chinese users
            Timber.i("Set default server provider to GitLab")
            userDataRepository.setRuleServerProvider(GITLAB)
        } else {
            Timber.i("Set default server provider to GitHub")
            userDataRepository.setRuleServerProvider(GITHUB)
        }
    }
}
