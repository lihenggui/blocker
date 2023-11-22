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
    override suspend fun disable(packageName: String) {
        Timber.i("Disabling $packageName")
        "pm disable $packageName".exec(ioDispatcher)
    }

    override suspend fun enable(packageName: String) {
        Timber.i("Enabling $packageName")
        "pm enable $packageName".exec(ioDispatcher)
    }

    override suspend fun clearCache(packageName: String, action: (Boolean) -> Unit) =
        withContext(ioDispatcher) {
            val cacheFolder = filesDir.parentFile
                ?.parentFile
                ?.resolve(packageName)
                ?.resolve("cache")
                ?: run {
                    Timber.e("Can't resolve cache path for $packageName")
                    action(false)
                    return@withContext
                }
            Timber.d("Delete cache folder: $cacheFolder")
            val result =
                FileUtils.delete(cacheFolder.absolutePath, recursively = true, ioDispatcher)
            action(result)
        }

    override suspend fun clearData(packageName: String, action: (Boolean) -> Unit) {
        Timber.i("Clearing data for $packageName")
        "pm clear $packageName".exec(ioDispatcher)
        action(true)
    }

    override suspend fun uninstallApp(packageName: String, action: (Int) -> Unit) {
        Timber.i("Uninstalling $packageName")
        "pm uninstall $packageName".exec(ioDispatcher)
        action(0)
    }
}
