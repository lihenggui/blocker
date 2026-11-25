/*
 * Copyright 2026 Blocker
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
import android.content.pm.PackageManager
import android.os.Process
import androidx.annotation.Px
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.utils.currentUserId
import me.zhanghai.android.appiconloader.AppIconLoader
import timber.log.Timber

/**
 * The icon of an app installed for any user, as Coil data. The APK is the same for every user, so
 * the icon is read from [apkPath]; the app need not be installed for the user Blocker runs in.
 *
 * @property uid the app's uid in its user; an app in a work profile gets the system's work badge
 * from it. Null for no badge.
 */
data class ApkIcon(
    val packageName: String,
    val apkPath: String?,
    val versionCode: Long,
    val uid: Int?,
)

class ApkIconKeyer : Keyer<ApkIcon> {
    override fun key(data: ApkIcon, options: Options): String = "${data.packageName}:${data.versionCode}:${data.uid}"
}

class ApkIconFetcher private constructor(
    private val loader: AppIconLoader,
    private val packageManager: PackageManager,
    private val icon: ApkIcon,
) : Fetcher {
    override suspend fun fetch(): ImageFetchResult {
        val info = applicationInfo() ?: error("No APK to read the icon of ${icon.packageName} from")
        val bitmap = try {
            loader.loadIcon(info)
        } catch (e: SecurityException) {
            // Badging asks whether the user is a work profile, which the system answers only
            // within Blocker's own profile group.
            Timber.w(e, "Cannot badge the icon of ${icon.packageName}, load it without a badge")
            loader.loadIcon(info.apply { uid = Process.myUid() })
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun applicationInfo(): ApplicationInfo? {
        val uid = icon.uid ?: Process.myUid()
        val ownUser = userOfUid(uid) == currentUserId().value
        // In Blocker's own user the installed app gives the same icon as the launcher shows.
        val installed = if (ownUser) installedInfo() else null
        return (installed ?: archiveInfo())?.apply { this.uid = uid }
    }

    private fun installedInfo(): ApplicationInfo? = try {
        packageManager.getApplicationInfo(icon.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun archiveInfo(): ApplicationInfo? {
        val path = icon.apkPath ?: return null
        val info = packageManager.getPackageArchiveInfo(path, 0)?.applicationInfo
        if (info == null) {
            Timber.w("Cannot read the APK of ${icon.packageName} at $path")
            return null
        }
        // An archive's resources are found through these paths, which parsing leaves unset.
        info.sourceDir = path
        info.publicSourceDir = path
        return info
    }

    class Factory(
        @Px iconSize: Int,
        shrinkNonAdaptiveIcons: Boolean,
        context: Context,
    ) : Fetcher.Factory<ApkIcon> {
        private val packageManager = context.packageManager
        private val loader = AppIconLoader(iconSize, shrinkNonAdaptiveIcons, context.applicationContext)

        override fun create(data: ApkIcon, options: Options, imageLoader: ImageLoader): Fetcher = ApkIconFetcher(loader, packageManager, data)
    }
}
