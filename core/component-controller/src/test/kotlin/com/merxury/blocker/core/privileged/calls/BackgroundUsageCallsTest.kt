// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.calls

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import android.content.pm.ApplicationInfo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.encodeOutcome
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.unmarshallCall
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundUsageCallsTest {
    private val work = UserId(10)
    private val system = FakeSystemServices()
    private val read = ReadBackgroundUsage(PACKAGE, work)
    private val restrict = SetBackgroundUsageAllowed(PACKAGE, work, allowed = false)

    @Before
    fun installInBothUsers() {
        for ((user, uid) in listOf(UserId.SYSTEM to OWNER_UID, work to WORK_UID)) {
            system.packageManager.install(user.value, PACKAGE)
            checkNotNull(packageInfo(system, PACKAGE, 0, user)).applicationInfo = ApplicationInfo().apply { this.uid = uid }
        }
    }

    @Test
    fun givenDifferentModesPerUser_whenRead_thenUseTheRequestedUsersUid() {
        system.appOps.modes[Triple(OP_RUN_ANY_IN_BACKGROUND, OWNER_UID, PACKAGE)] = MODE_IGNORED

        assertThat(roundTrip(read)).isEqualTo(CallResult.Success(BackgroundUsageState(MODE_ALLOWED, false, false)))
        assertThat(read.run(system).toBackgroundUsage()).isEqualTo(BackgroundUsage(allowed = true, changeable = true))
        assertThat(read.copy(user = UserId.SYSTEM).run(system).toBackgroundUsage())
            .isEqualTo(BackgroundUsage(allowed = false, changeable = true))
    }

    @Test
    fun givenSystemAllowlistEntry_whenReadAndWrite_thenUnchangeableAndNoChanges() {
        system.deviceIdle.systemAllowlist += PACKAGE

        assertThat(read.run(system).toBackgroundUsage()).isEqualTo(BackgroundUsage(allowed = true, changeable = false))
        assertThat(roundTrip(restrict)).isEqualTo(CallResult.Success(false))
        assertThat(system.backgroundUsageChanges).isEmpty()
    }

    @Test
    fun givenUserAllowlistEntry_whenRestrict_thenSetModeBeforeRemovingTheEntry() {
        system.deviceIdle.userAllowlist += PACKAGE
        assertThat(read.run(system).userAllowlisted).isTrue()

        assertThat(roundTrip(restrict)).isEqualTo(CallResult.Success(true))

        assertThat(system.backgroundUsageChanges)
            .containsExactly("mode $OP_RUN_ANY_IN_BACKGROUND $WORK_UID $PACKAGE $MODE_IGNORED", "remove $PACKAGE")
            .inOrder()
        assertThat(read.run(system)).isEqualTo(BackgroundUsageState(MODE_IGNORED, false, false))
        assertThat(read.copy(user = UserId.SYSTEM).run(system).mode).isEqualTo(MODE_ALLOWED)
    }

    @Test
    fun givenNoUserAllowlistEntry_whenRestrict_thenOnlyAppOpChanges() {
        system.deviceIdle.removeFailure = UnsupportedOperationException("No removable entry")

        assertThat(roundTrip(restrict)).isEqualTo(CallResult.Success(true))
        assertThat(system.backgroundUsageChanges).containsExactly("mode $OP_RUN_ANY_IN_BACKGROUND $WORK_UID $PACKAGE $MODE_IGNORED")
    }

    @Test
    fun givenUserAllowlistEntry_whenOptimize_thenModeAllowedAndEntryRemoved() {
        system.deviceIdle.userAllowlist += PACKAGE
        system.appOps.modes[Triple(OP_RUN_ANY_IN_BACKGROUND, WORK_UID, PACKAGE)] = MODE_IGNORED

        assertThat(roundTrip(restrict.copy(allowed = true))).isEqualTo(CallResult.Success(true))

        assertThat(system.backgroundUsageChanges)
            .containsExactly("mode $OP_RUN_ANY_IN_BACKGROUND $WORK_UID $PACKAGE $MODE_ALLOWED", "remove $PACKAGE")
            .inOrder()
        assertThat(read.run(system)).isEqualTo(BackgroundUsageState(MODE_ALLOWED, false, false))
    }

    @Test
    fun givenPackageMissingForUser_whenReadAndWrite_thenTargetNotFoundAndNoChanges() {
        val missing = UserId(11)
        val result = CallResult.Failure(CallFailure.TargetNotFound("$PACKAGE is not installed for user 11"))

        assertThat(roundTrip(read.copy(user = missing))).isEqualTo(result)
        assertThat(roundTrip(restrict.copy(user = missing))).isEqualTo(result)
        assertThat(system.backgroundUsageChanges).isEmpty()
    }

    @Test
    @Config(sdk = [27])
    fun givenAndroid8_whenReadAndWrite_thenRejectBeforeLookingUpThePackage() {
        system.packageManager.failure = IllegalStateException("Unexpected package lookup")

        assertThat(backgroundUsageSupported).isFalse()
        assertThrows(UnsupportedOperationException::class.java) { read.run(system) }
        assertThrows(UnsupportedOperationException::class.java) { restrict.run(system) }
        assertThat(system.backgroundUsageChanges).isEmpty()
    }

    @Test
    fun givenAppOpsRefusesWrite_whenRestrict_thenMapPermissionFailureAndLeaveAllowlist() {
        system.appOps.writeFailure = SecurityException("app ops denied")
        system.deviceIdle.userAllowlist += PACKAGE

        val result = roundTrip(restrict) as CallResult.Failure

        assertThat(result.reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat((result.reason as CallFailure.NotPermitted).message).isEqualTo("app ops denied")
        assertThat(system.backgroundUsageChanges).isEmpty()
        assertThat(read.run(system).userAllowlisted).isTrue()
    }

    @Test
    fun givenAllowlistRemovalFails_whenRetrySameWrite_thenFirstReportFailureAndThenComplete() {
        val error = IllegalStateException("device idle unavailable")
        system.deviceIdle.userAllowlist += PACKAGE
        system.deviceIdle.removeFailure = error

        assertThat(roundTrip(restrict)).isEqualTo(CallResult.Failure(CallFailure.SystemError(error.toString())))
        assertThat(read.run(system)).isEqualTo(BackgroundUsageState(MODE_IGNORED, false, true))

        system.deviceIdle.removeFailure = null
        assertThat(roundTrip(restrict)).isEqualTo(CallResult.Success(true))
        assertThat(read.run(system)).isEqualTo(BackgroundUsageState(MODE_IGNORED, false, false))
    }

    private fun <T> roundTrip(call: PrivilegedCall<T>): CallResult<T> = decodeOutcome(call, encodeOutcome(unmarshallCall(marshallCall(call)), system))

    private companion object {
        const val PACKAGE = "com.example.background"
        const val OWNER_UID = 10_123
        const val WORK_UID = 1_010_123
    }
}
