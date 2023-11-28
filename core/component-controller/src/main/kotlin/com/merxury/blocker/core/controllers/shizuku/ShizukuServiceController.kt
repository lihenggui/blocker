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

package com.merxury.blocker.core.controllers.shizuku

import android.app.ActivityManager
import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.utils.ContextUtils.userId
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject

private const val MAX_SERVICE_COUNT = 1000
private const val SHELL_PACKAGE_NAME = "com.android.shell"

class ShizukuServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : IServiceController {

    private var serviceList: List<ActivityManager.RunningServiceInfo> = listOf()

    private val am: IActivityManager by lazy {
        Timber.d("Get activity manager service from ShizukuBinderWrapper")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("activity"),
                ),
            )
        } else {
            ActivityManagerNative.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("activity"),
                ),
            )
        }
    }

    override suspend fun load(): Boolean {
        serviceList = am.getServices(MAX_SERVICE_COUNT, 0)
        return true
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        return serviceList.any {
            it.service.packageName == packageName && it.service.className == serviceName
        }
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean {
        val intent = Intent().apply {
            setComponent(ComponentName(packageName, serviceName))
        }
        val result = am.stopService(null, intent, intent.type, context.userId)
        return when (result) {
            0 -> {
                Timber.w("Service $packageName/$serviceName not stopped: was not running.")
                false
            }

            1 -> {
                Timber.i("Service $packageName/$serviceName stopped")
                false
            }

            -1 -> {
                Timber.e("Error stopping service $packageName/$serviceName")
                false
            }

            else -> true
        }
    }

    override suspend fun startService(packageName: String, serviceName: String): Boolean {
        val intent = Intent().apply {
            setComponent(ComponentName(packageName, serviceName))
        }
        val cn = am.startService(
            null,
            intent,
            intent.type,
            false,
            SHELL_PACKAGE_NAME,
            null,
            context.userId,
        )
        return if (cn == null) {
            Timber.e("Error: Cannot found $packageName/$serviceName; no service started.")
            false
        } else if (cn.packageName == "!") {
            Timber.e("Error in launching $packageName/$serviceName: Requires permission " + cn.className)
            false
        } else if (cn.packageName == "!!") {
            Timber.e("Error in launching $packageName/$serviceName:: " + cn.className)
            false
        } else if (cn.packageName == "?") {
            Timber.e("Error in launching $packageName/$serviceName:: " + cn.className)
            false
        } else {
            true
        }
    }
}
