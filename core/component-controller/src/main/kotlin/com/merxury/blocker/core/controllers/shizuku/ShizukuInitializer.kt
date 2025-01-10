/*
 * Copyright 2025 Blocker
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

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val REQUEST_CODE_PERMISSION = 101

@Singleton
internal class ShizukuInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
) : IShizukuInitializer {
    private var binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (Shizuku.isPreV11()) {
            Timber.e("Shizuku pre-v11 is not supported")
        } else {
            Timber.i("Shizuku binder received")
            checkAndAskForPermission()
        }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.e("Shizuku binder dead")
    }
    private var requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Timber.i("Shizuku permission granted")
                } else {
                    Timber.e("Shizuku permission denied")
                }
            }
        }

    override suspend fun registerShizuku(): RegisterShizukuResult = suspendCancellableCoroutine { cont ->
        binderReceivedListener = Shizuku.OnBinderReceivedListener {
            Timber.d("Shizuku binder received")
            if (hasPermission()) {
                cont.resume(RegisterShizukuResult(true, Shizuku.getUid()))
            } else {
                checkAndAskForPermission()
            }
        }
        requestPermissionResultListener =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == REQUEST_CODE_PERMISSION) {
                    val result = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        RegisterShizukuResult(true, Shizuku.getUid())
                    } else {
                        Timber.e("Shizuku permission denied")
                        RegisterShizukuResult(false, -1)
                    }
                    if (!cont.isCompleted) {
                        cont.resume(result)
                    } else {
                        Timber.w("Permission result received but coroutine is already completed")
                    }
                }
            }
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Timber.d("Register Shizuku finished")
    }

    override fun unregisterShizuku() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    override fun hasPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            if (e is IllegalStateException) {
                Timber.i("Shizuku is not initialized, no permission granted")
                return false
            }
            Timber.e(e, "Check Shizuku permission failed")
            false
        }
    }

    private fun checkAndAskForPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }
        try {
            return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                if (Sui.isSui()) {
                    val result = Sui.init(context.packageName)
                    Timber.d("Init Sui result: $result")
                }
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Timber.e("User denied Shizuku permission.")
                false
            } else {
                Timber.d("Request Shizuku permission")
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
                false
            }
        } catch (e: Throwable) {
            Timber.e(e, "Check Shizuku permission failed")
        }
        return false
    }

    override fun getUid(): Int = try {
        Shizuku.getUid()
    } catch (e: Throwable) {
        Timber.e(e, "Get uid failed")
        -1
    }
}
