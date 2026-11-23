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

package com.merxury.blocker.core.controllers.shizuku

import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.Denied
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.Failed
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.Granted
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.NotRunning
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.TimedOut
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult.UnsupportedVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import rikka.shizuku.Shizuku
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private const val SHELL_UID = 2000

@OptIn(ExperimentalCoroutinesApi::class)
class ShizukuInitializerTest {
    private val shizuku = FakeShizukuApi()
    private val initializer = ShizukuInitializer(shizuku)

    @Test
    fun givenShizukuNotRunning_whenRegister_thenReturnsNotRunningAfterBinderWait() = runTest {
        val result = initializer.registerShizuku()

        assertEquals(NotRunning, result)
        assertEquals(SHIZUKU_BINDER_TIMEOUT.inWholeMilliseconds, currentTime)
        shizuku.assertNoListeners()
    }

    @Test
    fun givenBinderArrivesLate_whenRegister_thenWaitsForBinder() = runTest {
        shizuku.permissionGranted = true

        val result = async { initializer.registerShizuku() }
        advanceTimeBy(SHIZUKU_BINDER_TIMEOUT.inWholeMilliseconds / 2)
        shizuku.startServer()

        assertEquals(Granted(SHELL_UID), result.await())
        shizuku.assertNoListeners()
    }

    @Test
    fun givenShizukuNotRunning_whenAwaitBinder_thenWaitsWithoutTimeoutUntilBinderArrives() = runTest {
        val wait = async { initializer.awaitBinder() }
        advanceTimeBy(1.hours)
        assertTrue(wait.isActive)
        assertEquals(1, shizuku.binderReceivedListeners.size)

        shizuku.startServer()
        wait.await()

        shizuku.assertNoListeners()
    }

    @Test
    fun givenPreV11Server_whenRegister_thenReturnsUnsupportedVersion() = runTest {
        shizuku.preV11 = true
        shizuku.startServer()

        assertEquals(UnsupportedVersion, initializer.registerShizuku())
        assertEquals(0, shizuku.requestPermissionCount)
        shizuku.assertNoListeners()
    }

    @Test
    fun givenPermissionDeniedEarlier_whenRegister_thenReturnsDeniedWithoutDialog() = runTest {
        shizuku.shouldShowRationale = true
        shizuku.startServer()

        assertEquals(Denied, initializer.registerShizuku())
        assertEquals(0, shizuku.requestPermissionCount)
        shizuku.assertNoListeners()
    }

    @Test
    fun givenPermissionAlreadyGranted_whenRegister_thenReturnsGrantedWithoutDialog() = runTest {
        shizuku.permissionGranted = true
        shizuku.startServer()

        assertEquals(Granted(SHELL_UID), initializer.registerShizuku())
        assertEquals(0, shizuku.requestPermissionCount)
        shizuku.assertNoListeners()
    }

    @Test
    fun givenUserGrantsDialog_whenRegister_thenReturnsGranted() = runTest {
        shizuku.startServer()

        val result = async { initializer.registerShizuku() }
        runCurrent()
        assertEquals(1, shizuku.requestPermissionCount)
        shizuku.answerPermissionDialog(granted = true)

        assertEquals(Granted(SHELL_UID), result.await())
        shizuku.assertNoListeners()
    }

    @Test
    fun givenUserDeniesDialog_whenRegister_thenReturnsDenied() = runTest {
        shizuku.startServer()

        val result = async { initializer.registerShizuku() }
        runCurrent()
        shizuku.answerPermissionDialog(granted = false)

        assertEquals(Denied, result.await())
        shizuku.assertNoListeners()
    }

    @Test
    fun givenDialogNeverAnswered_whenRegister_thenReturnsTimedOut() = runTest {
        shizuku.startServer()

        val result = initializer.registerShizuku()

        assertEquals(TimedOut, result)
        assertEquals(SHIZUKU_PERMISSION_TIMEOUT.inWholeMilliseconds, currentTime)
        shizuku.assertNoListeners()
    }

    @Test
    fun givenBinderReceivedTwice_whenRegister_thenCompletesOnce() = runTest {
        shizuku.permissionGranted = true

        val result = async { initializer.registerShizuku() }
        runCurrent()
        // Shizuku restarts before the waiting coroutine gets to run.
        shizuku.startServer()
        shizuku.startServer()

        assertEquals(Granted(SHELL_UID), result.await())
        shizuku.assertNoListeners()
        // A later restart must not reach a finished registration.
        shizuku.startServer()
    }

    @Test
    fun givenWaitingForBinder_whenCancelled_thenListenersRemoved() = runTest {
        val job = launch { initializer.registerShizuku() }
        runCurrent()
        assertEquals(1, shizuku.binderReceivedListeners.size)

        job.cancelAndJoin()

        shizuku.assertNoListeners()
        shizuku.startServer()
    }

    @Test
    fun givenWaitingForDialog_whenCancelled_thenListenersRemoved() = runTest {
        shizuku.startServer()
        val job = launch { initializer.registerShizuku() }
        runCurrent()
        assertEquals(1, shizuku.permissionResultListeners.size)

        job.cancelAndJoin()

        shizuku.assertNoListeners()
        shizuku.answerPermissionDialog(granted = true)
    }

    @Test
    fun givenTwoSequentialCalls_whenSecondWaitsForDialog_thenOneListenerSetRegistered() = runTest {
        shizuku.startServer()
        val first = async { initializer.registerShizuku() }
        runCurrent()
        shizuku.answerPermissionDialog(granted = false)
        assertEquals(Denied, first.await())

        val second = async { initializer.registerShizuku() }
        runCurrent()

        assertEquals(0, shizuku.binderReceivedListeners.size)
        assertEquals(1, shizuku.permissionResultListeners.size)
        shizuku.answerPermissionDialog(granted = true)
        assertEquals(Granted(SHELL_UID), second.await())
        shizuku.assertNoListeners()
    }

    @Test
    fun givenPermissionCheckThrows_whenRegister_thenReturnsFailed() = runTest {
        shizuku.checkPermissionError = RuntimeException("binder died")
        shizuku.startServer()

        val result = initializer.registerShizuku()

        assertIs<Failed>(result)
        assertEquals(shizuku.checkPermissionError, result.cause)
        shizuku.assertNoListeners()
    }

    @Test
    fun givenRequestPermissionThrows_whenRegister_thenReturnsFailed() = runTest {
        shizuku.requestPermissionError = RuntimeException("binder died")
        shizuku.startServer()

        val result = initializer.registerShizuku()

        assertIs<Failed>(result)
        assertEquals(shizuku.requestPermissionError, result.cause)
        shizuku.assertNoListeners()
    }
}

/**
 * Mimics the static Shizuku API: sticky binder listeners fire at once when the binder is ready,
 * and permission results reach every registered listener.
 */
private class FakeShizukuApi : ShizukuApi {
    var preV11 = false
    var permissionGranted = false
    var shouldShowRationale = false
    var checkPermissionError: Throwable? = null
    var requestPermissionError: Throwable? = null
    var requestPermissionCount = 0
        private set
    val binderReceivedListeners = mutableListOf<Shizuku.OnBinderReceivedListener>()
    val permissionResultListeners = mutableListOf<Shizuku.OnRequestPermissionResultListener>()
    private var binderReady = false
    private var lastRequestCode: Int? = null

    /** Delivers the binder, as Shizuku does each time it starts. */
    fun startServer() {
        binderReady = true
        binderReceivedListeners.toList().forEach { it.onBinderReceived() }
    }

    fun answerPermissionDialog(granted: Boolean) {
        permissionGranted = granted
        val requestCode = checkNotNull(lastRequestCode) { "Permission was never requested" }
        val grantResult = if (granted) PERMISSION_GRANTED else PERMISSION_DENIED
        permissionResultListeners.toList().forEach { it.onRequestPermissionResult(requestCode, grantResult) }
    }

    fun assertNoListeners() {
        assertTrue(binderReceivedListeners.isEmpty(), "Binder listeners left: $binderReceivedListeners")
        assertTrue(permissionResultListeners.isEmpty(), "Permission listeners left: $permissionResultListeners")
    }

    override fun isPreV11(): Boolean = preV11

    override fun pingBinder(): Boolean = binderReady

    override fun checkSelfPermission(): Int {
        checkPermissionError?.let { throw it }
        return if (permissionGranted) PERMISSION_GRANTED else PERMISSION_DENIED
    }

    override fun shouldShowRequestPermissionRationale(): Boolean = shouldShowRationale

    override fun requestPermission(requestCode: Int) {
        requestPermissionError?.let { throw it }
        requestPermissionCount++
        lastRequestCode = requestCode
    }

    override fun getUid(): Int = SHELL_UID

    override fun addBinderReceivedListenerSticky(listener: Shizuku.OnBinderReceivedListener) {
        if (binderReady) listener.onBinderReceived()
        binderReceivedListeners += listener
    }

    override fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        binderReceivedListeners -= listener
    }

    override fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        permissionResultListeners += listener
    }

    override fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        permissionResultListeners -= listener
    }
}
