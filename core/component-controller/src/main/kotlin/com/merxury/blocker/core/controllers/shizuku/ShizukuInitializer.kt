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

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.Denied
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.Failed
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.Granted
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.NotRunning
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.TimedOut
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.UnsupportedVersion
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val REQUEST_CODE_PERMISSION = 101

// Shizuku delivers its binder shortly after our process starts, so a missing binder right
// after a cold start does not yet mean Shizuku is stopped. Waiting a few seconds covers
// that delivery without holding up the "not running" result for long.
internal val SHIZUKU_BINDER_TIMEOUT = 5.seconds

// Long enough for the user to read and answer the permission dialog, even after a detour,
// but bounded so a dialog that never reports back cannot hold the result forever.
internal val SHIZUKU_PERMISSION_TIMEOUT = 2.minutes

@Singleton
internal class ShizukuInitializer @Inject constructor(
    private val shizuku: ShizukuApi,
) : IShizukuInitializer {

    override suspend fun registerShizuku(): RegisterShizukuResult {
        if (withTimeoutOrNull(SHIZUKU_BINDER_TIMEOUT) { awaitBinder() } == null) {
            Timber.w("No Shizuku binder within $SHIZUKU_BINDER_TIMEOUT, Shizuku is not running")
            return NotRunning
        }
        return try {
            when {
                shizuku.isPreV11() -> {
                    Timber.e("Shizuku pre-v11 is not supported")
                    UnsupportedVersion
                }

                shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Granted(getUid())

                shizuku.shouldShowRequestPermissionRationale() -> {
                    Timber.e("User denied Shizuku permission.")
                    Denied
                }

                else -> requestPermission()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Check Shizuku permission failed")
            Failed(e)
        }
    }

    // The sticky listener fires at once if the binder is already there and again on every
    // Shizuku restart, so it is removed as soon as the wait ends.
    override suspend fun awaitBinder() {
        var listener: Shizuku.OnBinderReceivedListener? = null
        try {
            suspendCancellableCoroutine { cont ->
                listener = Shizuku.OnBinderReceivedListener { cont.resumeIfActive(Unit) }
                    .also(shizuku::addBinderReceivedListenerSticky)
            }
        } finally {
            listener?.let(shizuku::removeBinderReceivedListener)
        }
    }

    private suspend fun requestPermission(): RegisterShizukuResult {
        var listener: Shizuku.OnRequestPermissionResultListener? = null
        try {
            val result = withTimeoutOrNull(SHIZUKU_PERMISSION_TIMEOUT) {
                suspendCancellableCoroutine { cont ->
                    listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                        if (requestCode == REQUEST_CODE_PERMISSION) {
                            val granted = grantResult == PackageManager.PERMISSION_GRANTED
                            Timber.i("Shizuku permission granted: $granted")
                            cont.resumeIfActive(if (granted) Granted(getUid()) else Denied)
                        }
                    }.also(shizuku::addRequestPermissionResultListener)
                    Timber.d("Request Shizuku permission")
                    // Report failures through the continuation. An exception thrown out of this
                    // block would leave the listener able to resume a call that already returned.
                    try {
                        shizuku.requestPermission(REQUEST_CODE_PERMISSION)
                    } catch (e: CancellationException) {
                        cont.cancel(e)
                    } catch (e: Throwable) {
                        Timber.e(e, "Request Shizuku permission failed")
                        cont.resumeIfActive(Failed(e))
                    }
                }
            }
            if (result == null) {
                Timber.w("Shizuku permission dialog not answered within $SHIZUKU_PERMISSION_TIMEOUT")
            }
            return result ?: TimedOut
        } finally {
            listener?.let(shizuku::removeRequestPermissionResultListener)
        }
    }

    override fun hasPermission(): Boolean {
        if (shizuku.isPreV11()) {
            return false
        }
        return try {
            shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is IllegalStateException) {
                Timber.i("Shizuku is not initialized, no permission granted")
                return false
            }
            Timber.e(e, "Check Shizuku permission failed")
            false
        }
    }

    override fun getUid(): Int = try {
        shizuku.getUid()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Timber.e(e, "Get uid failed")
        -1
    }
}

// Shizuku can deliver the same callback more than once, for example when it restarts.
private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
