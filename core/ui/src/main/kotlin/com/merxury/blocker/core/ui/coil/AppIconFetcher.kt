/*
 * Copyright 2026 Blocker
 * Copyright 2020 Google LLC
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

package com.merxury.blocker.core.ui.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.annotation.Px
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import me.zhanghai.android.appiconloader.AppIconLoader

class AppIconFetcher private constructor(
    private val loader: AppIconLoader,
    private val applicationInfo: ApplicationInfo,
) : Fetcher {
    override suspend fun fetch(): ImageFetchResult = ImageFetchResult(
        image = loader.loadIcon(applicationInfo).asImage(),
        isSampled = true,
        dataSource = DataSource.DISK,
    )

    class Factory(
        @Px iconSize: Int,
        shrinkNonAdaptiveIcons: Boolean,
        context: Context,
    ) : Fetcher.Factory<PackageInfo> {
        private val loader = AppIconLoader(iconSize, shrinkNonAdaptiveIcons, context.applicationContext)

        override fun create(data: PackageInfo, options: Options, imageLoader: ImageLoader): Fetcher? {
            val applicationInfo = data.applicationInfo ?: return null
            return AppIconFetcher(loader, applicationInfo)
        }
    }
}
