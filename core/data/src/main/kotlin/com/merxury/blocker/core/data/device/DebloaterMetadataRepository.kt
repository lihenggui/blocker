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

package com.merxury.blocker.core.data.device

import android.content.pm.PackageManager
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.getVersionCode
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.system.PlatformPackages
import com.merxury.blocker.core.utils.ManifestParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Manifest details and PackageManager order used by the debloater. Component state lives in the component cache. */
interface DebloaterMetadataRepository {
    /** PackageManager order for the specified user. App membership still comes from the cache. */
    suspend fun packageOrder(user: UserId): List<String>

    /** The live APK location, revision and permissions, or null if the package is gone. */
    suspend fun apk(app: AppKey): DebloaterApk?

    /** The base manifest of this APK, or null if it cannot be parsed. */
    suspend fun read(apk: DebloaterApk): AndroidManifest?
}

data class DebloaterApk(
    val app: AppKey,
    val path: String?,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val requestedPermissions: List<String>,
)

internal class LocalDebloaterMetadataRepository @Inject constructor(
    private val packages: PlatformPackages,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : DebloaterMetadataRepository {
    override suspend fun packageOrder(user: UserId): List<String> = withContext(ioDispatcher) {
        packages.installedPackages(user).map { it.packageName }
    }

    override suspend fun apk(app: AppKey): DebloaterApk? = withContext(ioDispatcher) {
        val info = try {
            packages.packageInfo(app.packageName, PackageManager.GET_PERMISSIONS, app.userId)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w(e, "Cannot find debloater app $app")
            return@withContext null
        }
        DebloaterApk(app, info.applicationInfo?.sourceDir, info.getVersionCode(), info.lastUpdateTime, info.requestedPermissions?.toList().orEmpty())
    }

    override suspend fun read(apk: DebloaterApk): AndroidManifest? {
        val path = apk.path ?: return null
        return ManifestParser.parseManifest(File(path), ioDispatcher).getOrElse {
            if (it is CancellationException) throw it
            Timber.w(it, "Cannot read the debloater manifest of ${apk.app}")
            null
        }
    }
}
