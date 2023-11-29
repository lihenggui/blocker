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

import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.merxury.blocker.core.controller.root.service.IRootService
import com.topjohnwu.superuser.ipc.RootService
import timber.log.Timber

class BlockerRootService : RootService() {
    override fun onCreate() {
        super.onCreate()
        Timber.d("RootService onCreate")
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("RootService onBind")
        return Ipc()
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

    class Ipc : IRootService.Stub() {
        override fun switchComponent(pkg: String?, cls: String?, state: Int): Boolean {
            TODO("Not yet implemented")
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
