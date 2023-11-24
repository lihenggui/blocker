/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.controllers.root

import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.utils.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class RootAppController @Inject constructor(
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IAppController {
    override suspend fun disable(packageName: String): Boolean {
        Timber.i("Disabling $packageName")
        val result = "pm disable $packageName".exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun enable(packageName: String): Boolean {
        Timber.i("Enabling $packageName")
        val result = "pm enable $packageName".exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun clearCache(packageName: String): Boolean =
        withContext(ioDispatcher) {
            val cacheFolder = filesDir.parentFile
                ?.parentFile
                ?.resolve(packageName)
                ?.resolve("cache")
                ?: run {
                    Timber.e("Can't resolve cache path for $packageName")
                    return@withContext false
                }
            Timber.d("Delete cache folder: $cacheFolder")
            return@withContext FileUtils.delete(
                cacheFolder.absolutePath,
                recursively = true,
                ioDispatcher,
            )
        }

    override suspend fun clearData(packageName: String): Boolean {
        Timber.i("Clearing data for $packageName")
        val result = "pm clear $packageName".exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun uninstallApp(packageName: String): Boolean {
        Timber.i("Uninstalling $packageName")
        val result = "pm uninstall $packageName".exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun forceStop(packageName: String): Boolean {
        Timber.i("Force stopping $packageName")
        val result = "am force-stop $packageName".exec(ioDispatcher)
        return result.isSuccess
    }
}
