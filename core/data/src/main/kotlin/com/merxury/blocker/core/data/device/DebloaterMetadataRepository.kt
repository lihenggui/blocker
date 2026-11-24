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
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.system.PlatformPackages
import com.merxury.blocker.core.system.UserNotReachableException
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

    /** The app's base manifest and requested permissions, or null if its manifest cannot be read. */
    suspend fun read(app: InstalledApp): DebloaterMetadata?
}

data class DebloaterMetadata(val manifest: AndroidManifest, val requestedPermissions: List<String>)

internal class LocalDebloaterMetadataRepository @Inject constructor(
    private val packages: PlatformPackages,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : DebloaterMetadataRepository {
    override suspend fun packageOrder(user: UserId): List<String> = withContext(ioDispatcher) {
        packages.installedPackages(user).map { it.packageName }
    }

    override suspend fun read(app: InstalledApp): DebloaterMetadata? = withContext(ioDispatcher) {
        val path = app.apkPath ?: return@withContext null
        val manifest = ManifestParser.parseManifest(File(path), ioDispatcher).getOrElse {
            Timber.w(it, "Cannot read the debloater manifest of ${app.key}")
            return@withContext null
        }
        val permissions = try {
            packages.packageInfo(app.key.packageName, PackageManager.GET_PERMISSIONS, app.key.userId)
                .requestedPermissions?.toList().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserNotReachableException) {
            throw e
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w(e, "Cannot find debloater app ${app.key}")
            return@withContext null
        } catch (e: Exception) {
            Timber.w(e, "Cannot read requested permissions for ${app.key}")
            emptyList()
        }
        DebloaterMetadata(manifest, permissions)
    }
}
