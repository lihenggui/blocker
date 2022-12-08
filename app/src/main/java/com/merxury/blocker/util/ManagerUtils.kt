/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.util

import android.os.Build
import android.util.Log
import com.merxury.blocker.BlockerApplication
import com.merxury.blocker.core.extension.exec
import com.topjohnwu.superuser.io.SuFile

object ManagerUtils {
    @Throws(RuntimeException::class)
    suspend fun launchApplication(packageName: String) {
        "monkey -p $packageName -c android.intent.category.LAUNCHER 1".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun launchActivity(packageName: String, activityName: String) {
        "am start -n $packageName/$activityName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun forceStop(packageName: String) {
        "am force-stop $packageName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun startService(packageName: String, serviceName: String) {
        "am startservice $packageName/$serviceName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun stopService(packageName: String, serviceName: String) {
        "am stopservice $packageName/$serviceName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun disableApplication(packageName: String) {
        "pm disable $packageName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun enableApplication(packageName: String) {
        "pm enable $packageName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun clearData(packageName: String) {
        "pm clear $packageName".exec()
    }

    @Throws(RuntimeException::class)
    suspend fun clearCache(packageName: String) {
        val context = BlockerApplication.context
        // TODO API adaption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dataFolder = context.dataDir
                .parentFile
                ?.resolve(packageName)
                ?.resolve("cache")
            if (dataFolder == null) {
                Log.e("ManagerUtils", "Can't find cache folder for $packageName")
                return
            }
            val cacheFolder = SuFile(dataFolder.absolutePath)
            if (cacheFolder.exists()) {
                cacheFolder.deleteRecursive()
            }
        }
    }

    @Throws(RuntimeException::class)
    suspend fun uninstallApplication(packageName: String) {
        "pm uninstall $packageName".exec()
    }
}
