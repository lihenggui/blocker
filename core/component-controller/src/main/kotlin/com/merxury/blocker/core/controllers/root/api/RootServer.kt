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

package com.merxury.blocker.core.controllers.root.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.merxury.blocker.core.controller.root.service.IRootService
import com.merxury.blocker.core.utils.ContextUtils.userId
import com.topjohnwu.superuser.ipc.RootService
import timber.log.Timber

class RootServer : RootService() {
    override fun onCreate() {
        super.onCreate()
        Timber.d("RootService onCreate")
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("RootService onBind")
        return Ipc(this)
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Timber.d("AIDLService: onRebind, daemon process reused")
    }

    override fun onUnbind(intent: Intent): Boolean {
        super.onUnbind(intent)
        Timber.d("AIDLService: onUnbind, client process unbound")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("RootService onDestroy")
    }

    class Ipc(private val context: Context) : IRootService.Stub() {
        private val pm: IPackageManager by lazy {
            IPackageManager.Stub.asInterface(
                SystemServiceHelper.getSystemService("package")
            )
        }
        override fun switchComponent(packageName: String?, componentName: String?, state: Int): Boolean {
            if (packageName == null || componentName == null) {
                Timber.w("Invalid component info provided")
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, componentName),
                    state,
                    0,
                    context.userId,
                    context.packageName,
                )
            } else {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, componentName),
                    state,
                    0,
                    context.userId,
                )
            }
            return true
        }

        override fun getUid(): Int {
            val uid = Process.myUid()
            Timber.d("uid: $uid")
            return uid
        }

        override fun getPid(): Int {
            val pid = Process.myPid()
            Timber.d("pid: $pid")
            return pid
        }
    }
}
