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

package com.merxury.blocker.core.privileged.calls

import android.content.ComponentName
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.unmarshallCall
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val COMPONENT = ComponentName("com.example", "com.example.MainActivity")
private val WORK = UserId(10)

@RunWith(RobolectricTestRunner::class)
class ActivityCallsTest {

    private val system = FakeSystemServices()
    private val am = system.activityManager

    @Test
    @Config(sdk = [29])
    fun givenAndroid10_whenStart_thenOldSignatureWithNewTaskForThatUser() {
        assertThat(StartActivity(COMPONENT, WORK).run(system)).isEqualTo(0)

        assertThat(am.activityStarts).containsExactly("start11 ${system.callerPackage} com.example/.MainActivity ${Intent.FLAG_ACTIVITY_NEW_TASK} 10")
    }

    @Test
    @Config(sdk = [32, 36])
    fun givenAndroid11OrLater_whenStart_thenSignatureWithFeature() {
        StartActivity(COMPONENT, WORK).run(system)

        assertThat(am.activityStarts).containsExactly("start12 ${system.callerPackage} com.example/.MainActivity ${Intent.FLAG_ACTIVITY_NEW_TASK} 10")
    }

    @Test
    @Config(sdk = [28, 35])
    fun givenShell_whenStart_thenShellPackageIsTheCaller() {
        system.callerUid = 2000

        StartActivity(COMPONENT, UserId.SYSTEM).run(system)

        assertThat(am.activityStarts.single()).contains(" com.android.shell ")
    }

    @Test
    @Config(sdk = [35])
    fun givenStartResult_whenRun_thenReturnedForMapping() {
        am.startActivityResult = 102

        assertThat(StartActivity(COMPONENT, WORK).run(system)).isEqualTo(102)
    }

    @Test
    @Config(sdk = [35])
    fun whenMarshalled_thenSameCallArrivesAndRunsThroughBinderOnly() {
        val call = StartActivity(COMPONENT, WORK)

        assertThat(unmarshallCall(marshallCall(call))).isEqualTo(call)
        assertThat(call.needsPrivilegedProcess).isFalse()
    }

    @Test
    @Config(sdk = [35])
    fun givenSuccessCodes_whenMapped_thenStartedOnEveryLevel() {
        listOf(24, 25, 26, 28, 34, 36).forEach { sdk ->
            (0..3).forEach { code -> assertThat(startOutcome(code, sdk)).isEqualTo(CallResult.Success(Unit)) }
        }
    }

    @Test
    @Config(sdk = [35])
    fun givenAndroid7Codes_whenMapped_thenTyped() {
        assertThat(startOutcome(-4, 25).failure()).isInstanceOf(CallFailure.NotPermitted::class.java)
        listOf(-1, -2, -5).forEach { assertThat(startOutcome(it, 24).failure()).isInstanceOf(CallFailure.TargetNotFound::class.java) }
        assertThat(startOutcome(-6, 25)).isEqualTo(CallResult.Failure(CallFailure.SystemError("Activity start failed with result -6")))
        // From 8.0 these numbers mean nothing; only the -100 based codes do.
        assertThat(startOutcome(-94, 25).failure()).isInstanceOf(CallFailure.SystemError::class.java)
    }

    @Test
    @Config(sdk = [35])
    fun givenAndroid8OrLaterCodes_whenMapped_thenTyped() {
        listOf(26, 28, 34, 36).forEach { sdk ->
            assertThat(startOutcome(-94, sdk).failure()).isInstanceOf(CallFailure.NotPermitted::class.java)
            listOf(-91, -92, -95).forEach { assertThat(startOutcome(it, sdk).failure()).isInstanceOf(CallFailure.TargetNotFound::class.java) }
            // START_ABORTED, which the system turns into START_SUCCESS before answering, and START_CANCELED.
            assertThat(startOutcome(102, sdk)).isEqualTo(CallResult.Failure(CallFailure.SystemError("Activity start failed with result 102")))
            assertThat(startOutcome(-96, sdk).failure()).isInstanceOf(CallFailure.SystemError::class.java)
            assertThat(startOutcome(-4, sdk).failure()).isInstanceOf(CallFailure.SystemError::class.java)
        }
    }

    private fun CallResult<Unit>.failure(): CallFailure = (this as CallResult.Failure).reason
}
