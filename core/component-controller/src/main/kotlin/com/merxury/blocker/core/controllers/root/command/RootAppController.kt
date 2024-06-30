/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.controllers.root.command

import android.content.Context
import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.controllers.utils.ContextUtils.userId
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.utils.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootAppController @Inject constructor(
    @ApplicationContext private val context: Context,
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IAppController {
    private val runningAppList = mutableSetOf<String>()

    override suspend fun disable(packageName: String): Boolean {
        Timber.i("Disabling $packageName")
        val result = "pm disable --user ${context.userId} $packageName"
            .exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun enable(packageName: String): Boolean {
        Timber.i("Enabling $packageName")
        val result = "pm enable --user ${context.userId} $packageName"
            .exec(ioDispatcher)
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
        val result = "pm clear --user ${context.userId} $packageName"
            .exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean {
        Timber.i("Uninstalling $packageName")
        val result = "pm uninstall --user ${context.userId} $packageName"
            .exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun forceStop(packageName: String): Boolean {
        Timber.i("Force stopping $packageName")
        val result = "am force-stop $packageName"
            .exec(ioDispatcher)
        return result.isSuccess
    }

    override suspend fun refreshRunningAppList() {
        try {
            val commandResult = "ps -A -o NAME"
                .exec(ioDispatcher)
            if (!commandResult.isSuccess) {
                Timber.e("Failed to get running app list: ${commandResult.err}")
                return
            }
            runningAppList.clear()
            runningAppList.addAll(commandResult.out)
        } catch (e: Exception) {
            Timber.w("Failed to refresh running app list")
        }
    }

    override fun isAppRunning(packageName: String): Boolean = runningAppList.contains(packageName)
}
