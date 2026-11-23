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
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.unmarshallCall
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val COMPONENT = ComponentName("com.example", "com.example.Wake")
private val WORK = UserId(10)

/** Runs on Android 13, which sets a component without a calling package, and 14, which takes one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34])
class SetComponentEnabledIfCurrentTest {

    private val system = FakeSystemServices()
    private val pm = system.packageManager

    private fun setting(user: UserId = WORK) = pm.componentSettings[COMPONENT to user.value] ?: COMPONENT_ENABLED_STATE_DEFAULT

    private fun run(expected: Int, newState: Int, user: UserId = WORK) = SetComponentEnabledIfCurrent(COMPONENT, user, expected, newState).run(system)

    private val setCall = if (Build.VERSION.SDK_INT >= 34) "setComponentEnabledSetting/${system.callerPackage}" else "setComponentEnabledSetting"

    @Test
    fun givenExpectedSetting_whenRun_thenChangedWithoutKillingAndTrue() {
        assertThat(run(COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)).isTrue()

        assertThat(setting()).isEqualTo(COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(pm.changes).containsExactly(setCall)
        assertThat(pm.componentFlags).containsExactly(DONT_KILL_APP)
    }

    @Test
    fun givenAlreadyTargetSetting_whenRun_thenTrueWithoutChange() {
        pm.componentSettings[COMPONENT to WORK.value] = COMPONENT_ENABLED_STATE_DISABLED

        assertThat(run(COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)).isTrue()
        assertThat(pm.changes).isEmpty()
    }

    @Test
    fun givenOtherSetting_whenRun_thenFalseWithoutChange() {
        pm.componentSettings[COMPONENT to WORK.value] = COMPONENT_ENABLED_STATE_ENABLED

        assertThat(run(COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)).isFalse()
        assertThat(setting()).isEqualTo(COMPONENT_ENABLED_STATE_ENABLED)
        assertThat(pm.changes).isEmpty()
    }

    @Test
    fun givenSystemIgnoresChange_whenRun_thenFalse() {
        pm.ignoreComponentSettingChanges = true

        assertThat(run(COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)).isFalse()
        assertThat(pm.changes).containsExactly(setCall)
    }

    @Test
    fun givenRestoreToDefault_whenRun_thenDefaultForThatUserOnly() {
        pm.componentSettings[COMPONENT to WORK.value] = COMPONENT_ENABLED_STATE_DISABLED
        pm.componentSettings[COMPONENT to 0] = COMPONENT_ENABLED_STATE_DISABLED

        assertThat(run(COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT)).isTrue()

        assertThat(setting()).isEqualTo(COMPONENT_ENABLED_STATE_DEFAULT)
        assertThat(setting(UserId.SYSTEM)).isEqualTo(COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun givenNotAComponentSetting_whenCreated_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SetComponentEnabledIfCurrent(COMPONENT, WORK, COMPONENT_ENABLED_STATE_DISABLED_USER, COMPONENT_ENABLED_STATE_DEFAULT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SetComponentEnabledIfCurrent(COMPONENT, WORK, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED_USER)
        }
    }

    @Test
    fun whenMarshalled_thenSameCallArrivesAndRunsThroughBinderOnly() {
        val call = SetComponentEnabledIfCurrent(COMPONENT, WORK, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)

        assertThat(unmarshallCall(marshallCall(call))).isEqualTo(call)
        assertThat(call.needsPrivilegedProcess).isFalse()
    }
}
