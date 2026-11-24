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

package com.merxury.blocker.core.controllers.wake

import android.content.ComponentName
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.control.PackageLocks
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.ScriptedExecutor
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledIfCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private const val PKG = "com.example"
private const val CLS = "com.example.Wake"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManagedComponentStateTest {
    private val executor = ScriptedExecutor().apply { answer = { CallResult.Success(true) } }
    private val locks = PackageLocks()
    private val components = ChannelManagedComponentState(executor, locks)

    @Test fun whenComparingAndSetting_thenOneCallForTheGivenUserWithoutConnecting() = runTest {
        assertThat(components.compareAndSet(PKG, CLS, UserId(10), COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)).isTrue()

        assertThat(executor.runs).containsExactly(
            SetComponentEnabledIfCurrent(ComponentName(PKG, CLS), UserId(10), COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED) to false,
        )
    }

    @Test fun givenUsersChangeOfThePackageRunning_whenComparingAndSetting_thenWaitsForIt() = runTest {
        val usersChange = CompletableDeferred<Unit>()
        launch { locks.withLock(PKG) { usersChange.await() } }
        runCurrent()

        val change = async { components.compareAndSet(PKG, CLS, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT) }
        runCurrent()
        assertThat(executor.runs).isEmpty()

        usersChange.complete(Unit)
        assertThat(change.await()).isTrue()
        assertThat(executor.runs).hasSize(1)
    }

    @Test fun givenOtherPackageLocked_whenComparingAndSetting_thenRunsAtOnce() = runTest {
        val other = CompletableDeferred<Unit>()
        launch { locks.withLock("com.other") { other.await() } }
        runCurrent()

        assertThat(components.compareAndSet(PKG, CLS, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)).isTrue()
        other.complete(Unit)
    }

    @Test fun whenProceedRuns_thenItHoldsThePackageLock() = runTest {
        var lockedDuringProceed = false
        components.compareAndSet(PKG, CLS, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT) {
            // Another change of the package cannot start until this one ends.
            val probe = async { locks.withLock(PKG) { } }
            runCurrent()
            lockedDuringProceed = !probe.isCompleted
            true
        }

        assertThat(lockedDuringProceed).isTrue()
    }

    @Test fun givenProceedRefuses_whenComparingAndSetting_thenFalseAndNothingSent() = runTest {
        assertThat(components.compareAndSet(PKG, CLS, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT) { false }).isFalse()

        assertThat(executor.runs).isEmpty()
    }

    @Test fun givenSettingChangedMeanwhile_whenComparingAndSetting_thenFalse() = runTest {
        executor.answer = { CallResult.Success(false) }

        assertThat(components.compareAndSet(PKG, CLS, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT)).isFalse()
    }

    @Test fun givenChannelNotReady_whenComparingAndSetting_thenThrowsWithoutConnecting() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.ChannelUnavailable) }

        val thrown = runCatching { components.compareAndSet(PKG, CLS, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(executor.runs.single().second).isFalse()
    }
}
