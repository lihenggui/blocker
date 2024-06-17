/*
 * Copyright 2024 Blocker
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
import android.os.Build
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.analytics.StubAnalyticsHelper
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.logging.ReleaseTree
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.core.rule.work.CopyRulesToStorageWorker
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.sync.initializers.Sync
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

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
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var profileVerifierLogger: ProfileVerifierLogger

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var releaseTree: ReleaseTree

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (ApplicationUtil.isDebugMode(this)) {
            Timber.plant(Timber.DebugTree())
            // Kill the app if there are main thread policy violations.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            detectUnbufferedIo()
                        }
                        detectDiskWrites()
                        detectCustomSlowCalls()
                        detectNetwork()
                        penaltyLog()
                        penaltyDeath()
                    }
                    .build(),
            )
            Shell.enableVerboseLogging = true
        }
        Timber.plant(releaseTree)
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
        initAppSettings()
        addApiExemptions()
        profileVerifierLogger()
        Sync.initialize(context = this)
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    private fun initAppSettings() {
        applicationScope.launch {
            val userData = userDataRepository.userData.first()
            val allowStatistics = analyticsHelper !is StubAnalyticsHelper && userData.enableStatistics
            userDataRepository.setEnableStatistics(allowStatistics)
            analyticsHelper.setEnableStatistics(allowStatistics)
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
                CopyRulesToStorageWorker.WORK_NAME,
                KEEP,
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

    private fun addApiExemptions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Timber.i("Add hidden API exemptions")
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
