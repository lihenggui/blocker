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

package com.merxury.blocker.core.privileged.shizuku

import rikka.shizuku.Shizuku
import javax.inject.Inject

/**
 * The static [Shizuku] calls used by [DefaultShizukuInitializer], behind an interface so its
 * registration logic can be tested without a Shizuku binder.
 */
internal interface ShizukuApi {
    fun isPreV11(): Boolean
    fun checkSelfPermission(): Int
    fun shouldShowRequestPermissionRationale(): Boolean
    fun requestPermission(requestCode: Int)
    fun getUid(): Int

    /** Whether the Shizuku binder is there and alive. Never throws. */
    fun pingBinder(): Boolean
    fun addBinderReceivedListenerSticky(listener: Shizuku.OnBinderReceivedListener)

    /** Called for each binder Shizuku delivers from now on, not for one already there. */
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener)
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener)
    fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener)
}

internal class DefaultShizukuApi @Inject constructor() : ShizukuApi {
    override fun isPreV11(): Boolean = Shizuku.isPreV11()

    override fun checkSelfPermission(): Int = Shizuku.checkSelfPermission()

    override fun shouldShowRequestPermissionRationale(): Boolean = Shizuku.shouldShowRequestPermissionRationale()

    override fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    override fun getUid(): Int = Shizuku.getUid()

    override fun pingBinder(): Boolean = Shizuku.pingBinder()

    override fun addBinderReceivedListenerSticky(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.addBinderReceivedListenerSticky(listener)
    }

    override fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.addBinderReceivedListener(listener)
    }

    override fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.removeBinderReceivedListener(listener)
    }

    override fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    override fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    override fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.addBinderDeadListener(listener)
    }
}
