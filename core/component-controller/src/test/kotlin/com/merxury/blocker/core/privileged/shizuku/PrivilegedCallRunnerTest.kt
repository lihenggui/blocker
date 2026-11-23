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

import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.EchoInPrivilegedProcess
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.MeasureInPrivilegedProcess
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.awaitPipeWriters
import com.merxury.blocker.core.privileged.calls.ForceStop
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.marshallCall
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The checks the user service makes before it runs anything. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivilegedCallRunnerTest {

    private val system = FakeSystemServices()
    private var callerUid = BLOCKER_UID
    private val runner = PrivilegedCallRunner(BLOCKER_UID, { callerUid }, system)

    @Test
    fun givenBlockerCalls_whenRun_thenCallRunsAndOutcomeArrives() {
        val call = EchoInPrivilegedProcess("hi")

        val reply = runner.run(request(call))

        assertThat(decodeOutcome(call, reply.read())).isEqualTo(CallResult.Success("hi for ${system.callerPackage}"))
    }

    @Test
    fun givenOtherUidCalls_whenRun_thenSecurityException() {
        callerUid = BLOCKER_UID + 1

        assertThrows(SecurityException::class.java) { runner.run(request(EchoInPrivilegedProcess("hi"))) }
    }

    @Test
    fun givenShizukuPassedNoContext_whenRun_thenEveryCallerRefused() {
        val withoutContext = PrivilegedCallRunner(allowedUid = null, callingUid = { BLOCKER_UID }, system = system)

        assertThrows(SecurityException::class.java) { withoutContext.run(request(EchoInPrivilegedProcess("hi"))) }
    }

    @Test
    fun givenCallThatNeedsNoPrivilegedProcess_whenRun_thenSecurityExceptionAndNotRun() {
        assertThrows(SecurityException::class.java) { runner.run(request(ForceStop("com.example", UserId(10)))) }
        assertThat(system.activityManager.forceStopped).isEmpty()
    }

    @Test
    fun givenPlatformParcelable_whenRun_thenSecurityException() {
        val bytes = parcelOf(Intent::class.java.name, Intent("com.example.ACTION"))

        assertThrows(SecurityException::class.java) { runner.run(CallBytes.request(bytes)) }
    }

    @Test
    fun givenOwnClassThatIsNoCall_whenRun_thenSecurityException() {
        val bytes = parcelOf(CallBytes::class.java.name, CallBytes.reply(byteArrayOf(1, 2, 3)))

        assertThrows(SecurityException::class.java) { runner.run(CallBytes.request(bytes)) }
    }

    @Test
    fun givenUnknownClass_whenRun_thenSecurityException() {
        val bytes = parcelOf("com.example.NotThere", null)

        assertThrows(SecurityException::class.java) { runner.run(CallBytes.request(bytes)) }
    }

    @Test
    fun givenLargeCall_whenRun_thenReadThroughPipeAndRun() {
        val call = MeasureInPrivilegedProcess("b".repeat(200_000))
        val request = request(call)
        assertThat(awaitPipeWriters()).isTrue()

        val reply = runner.run(request)

        assertThat(decodeOutcome(call, reply.read())).isEqualTo(CallResult.Success(200_000))
    }

    private fun request(call: PrivilegedCall<*>) = CallBytes.request(marshallCall(call))

    private fun parcelOf(className: String, value: Parcelable?): ByteArray {
        val parcel = Parcel.obtain()
        try {
            parcel.writeString(className)
            value?.writeToParcel(parcel, 0)
            return parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }
}
