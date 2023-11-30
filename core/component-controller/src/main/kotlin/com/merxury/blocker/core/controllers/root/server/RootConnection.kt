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

package com.merxury.blocker.core.controllers.root.server

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.merxury.blocker.core.controller.root.service.IRootService
import timber.log.Timber

class RootConnection : ServiceConnection {
    private var isDaemon = false
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Timber.d("onServiceConnected")
        val ipc = IRootService.Stub.asInterface(service)
        Timber.d("uid: ${ipc.uid}")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.d("onServiceDisconnected")
    }
}
