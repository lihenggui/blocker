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
import coil.decode.SvgDecoder
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import timber.log.Timber

/**
 * [Application] class for Blocker
 */
@HiltAndroidApp
class BlockerApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
    }

    /**
     * Since we're displaying SVGs and application icons in the app, Coil needs an ImageLoader
     * which supports this format. During Coil's initialization it will call
     * `applicationContext.newImageLoader()` to obtain an ImageLoader.
     *
     * @see https://github.com/coil-kt/coil/blob/main/coil-singleton/src/main/java/coil/Coil.kt#L63
     */
    override fun newImageLoader(): ImageLoader {
        val iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size)
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, true, this@BlockerApplication))
            }
            .build()
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
