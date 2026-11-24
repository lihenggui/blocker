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

package com.merxury.blocker.core.privileged

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.Parcel
import android.os.Parcelable
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.privileged.calls.ComponentEnabledSettings
import com.merxury.blocker.core.privileged.calls.GetPackageInfo
import com.merxury.blocker.core.privileged.calls.InstalledPackages
import com.merxury.blocker.core.privileged.calls.ListUsers
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
import com.merxury.blocker.core.privileged.root.RootCallCommand
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private const val PKG = "com.example"
private val WORK = UserId(10)

/** What travels back from a privileged process: each kind of result and every failure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallOutcomeTest {

    private val system = FakeSystemServices()

    private fun <R> roundTrip(call: PrivilegedCall<R>): CallResult<R> = decodeOutcome(call, encodeOutcome(call, system))

    @Test
    fun givenUsers_whenRoundTrip_thenEveryFieldKept() {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work", flags = 0x20, parent = 0)

        assertThat(roundTrip(ListUsers)).isEqualTo(
            CallResult.Success(
                listOf(
                    DeviceUser(UserId(0), "Owner", UserKind.OWNER, parent = null, running = true, unlocked = true),
                    DeviceUser(WORK, "Work", UserKind.WORK_PROFILE, parent = UserId(0), running = false, unlocked = false),
                ),
            ),
        )
    }

    @Test
    fun givenPackages_whenRoundTrip_thenPackageInfosKept() {
        system.userManager.addUser(10, "Work")
        system.packageManager.install(10, PKG)
        system.packageManager.install(10, "com.other")

        val result = roundTrip(InstalledPackages(WORK)) as CallResult.Success

        assertThat(result.value.map { it.packageName }).containsExactly(PKG, "com.other").inOrder()
    }

    @Test
    fun givenUnreadableSettings_whenRoundTrip_thenNullsKept() {
        system.packageManager.install(10, PKG)
        system.packageManager.rejectedComponents += ComponentName(PKG, "$PKG.Gone")

        assertThat(roundTrip(ComponentEnabledSettings(PKG, listOf("$PKG.Main", "$PKG.Gone"), WORK)))
            .isEqualTo(CallResult.Success(listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, null)))
    }

    @Test
    fun givenAction_whenRoundTrip_thenUnit() {
        val call = SetComponentEnabledSettings(
            listOf(ComponentName(PKG, "$PKG.Main")),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            WORK,
        )

        assertThat(roundTrip(call)).isEqualTo(CallResult.Success(Unit))
    }

    @Test
    fun givenSystemThrows_whenRoundTrip_thenEachKindMapped() {
        val call = GetPackageInfo(PKG, 0, WORK)
        val cases = mapOf(
            SecurityException("Shell cannot change component state") to
                CallFailure.NotPermitted("Shell cannot change component state"),
            IllegalArgumentException("Unknown package") to CallFailure.TargetNotFound("Unknown package"),
            DeadObjectException() to CallFailure.ChannelUnavailable,
            IllegalStateException("odd") to CallFailure.SystemError("java.lang.IllegalStateException: odd"),
        )

        cases.forEach { (thrown, expected) ->
            system.packageManager.failure = thrown
            assertThat(roundTrip(call)).isEqualTo(CallResult.Failure(expected))
        }
    }

    @Test
    fun givenMissingPackage_whenRoundTrip_thenTargetNotFound() {
        val result = roundTrip(GetPackageInfo(PKG, 0, WORK))

        assertThat((result as CallResult.Failure).reason).isInstanceOf(CallFailure.TargetNotFound::class.java)
    }

    @Test
    @Suppress("DEPRECATION")
    fun givenSmallCall_whenCommandParceled_thenSameCallArrivesInline() {
        val call = ComponentEnabledSettings(PKG, listOf("$PKG.Main"), WORK)
        val request = CallBytes.request(marshallCall(call))

        val arrived = crossed(RootCallCommand(request)) { it.readParcelable<RootCallCommand>(RootCallCommand::class.java.classLoader)!! }

        assertThat(request.describeContents()).isEqualTo(0)
        assertThat(unmarshallCall(arrived.request.read())).isEqualTo(call)
    }

    @Test
    fun givenLargeCall_whenRequested_thenPipedAndWholeCallRead() {
        val call = EchoInPrivilegedProcess("a".repeat(200_000))
        val request = CallBytes.request(marshallCall(call))

        assertThat(request.describeContents()).isEqualTo(Parcelable.CONTENTS_FILE_DESCRIPTOR)
        assertThat(awaitPipeWriters()).isTrue()
        assertThat(unmarshallCall(request.read(CallBytes.MAX_REQUEST_BYTES))).isEqualTo(call)
        request.close()
    }

    @Test
    fun givenRequestLargerThanReaderAllows_whenRead_thenIoException() {
        val request = CallBytes.request(ByteArray(300 * 1024))
        assertThat(awaitPipeWriters()).isTrue()

        assertThrows(IOException::class.java) { request.read(maxBytes = 100 * 1024) }
        request.close()
    }

    @Test
    fun givenRequestAboveCap_whenCreated_thenIllegalState() {
        assertThrows(IllegalStateException::class.java) { CallBytes.request(ByteArray(CallBytes.MAX_REQUEST_BYTES + 1)) }
    }

    @Test
    fun givenSmallOutcome_whenReplyParceled_thenSameBytesRead() {
        val bytes = encodeOutcome(ListUsers, system)

        assertThat(crossed(CallBytes.reply(bytes)) { CallBytes.CREATOR.createFromParcel(it) }.read()).isEqualTo(bytes)
    }

    @Test
    fun givenLargeOutcome_whenReplyParceled_thenSameBytesReadThroughPipe() {
        val bytes = ByteArray(200 * 1024) { it.toByte() }
        val reply = CallBytes.reply(bytes)

        assertThat(reply.describeContents()).isEqualTo(Parcelable.CONTENTS_FILE_DESCRIPTOR)
        assertThat(crossed(reply) { CallBytes.CREATOR.createFromParcel(it) }.read()).isEqualTo(bytes)
    }

    /** Writes [value] to a parcel and reads it back with [read], as a binder call would. */
    private fun <T : Parcelable> crossed(value: Parcelable, read: (Parcel) -> T): T {
        val parcel = Parcel.obtain()
        try {
            if (value is RootCallCommand) parcel.writeParcelable(value, 0) else value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return read(parcel)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun givenCallThatFailsInPrivilegedProcess_whenRoundTrip_thenFailureKept() {
        assertThat(roundTrip(FailInPrivilegedProcess("no"))).isEqualTo(CallResult.Failure(CallFailure.NotPermitted("no")))
    }
}
