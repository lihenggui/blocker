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

package com.merxury.blocker.di

import android.content.Context
import androidx.tracing.trace
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.addLastModifiedToFileCacheKey
import coil3.svg.SvgDecoder
import com.merxury.blocker.R
import com.merxury.blocker.core.ui.coil.AppIconFetcher
import com.merxury.blocker.core.ui.coil.AppIconKeyer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Call
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {
    /**
     * Since we're displaying SVGs in the app, Coil needs an ImageLoader which supports this
     * format. Coil obtains this loader through BlockerApplication's SingletonImageLoader.Factory.
     *
     * @see <a href="https://coil-kt.github.io/coil/image_loaders/">Coil image loaders</a>
     */
    @Provides
    @Singleton
    fun imageLoader(
        // We specifically request dagger.Lazy here, so that it's not instantiated from Dagger.
        okHttpCallFactory: dagger.Lazy<Call.Factory>,
        @ApplicationContext application: Context,
    ): ImageLoader = trace("BlockerImageLoader") {
        val iconSize = application.resources.getDimensionPixelSize(R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            // Refresh local rule icons when their files change after synchronization.
            .addLastModifiedToFileCacheKey(true)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpCallFactory.get() }))
                add(SvgDecoder.Factory())
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, true, application))
            }
            .build()
    }
}
