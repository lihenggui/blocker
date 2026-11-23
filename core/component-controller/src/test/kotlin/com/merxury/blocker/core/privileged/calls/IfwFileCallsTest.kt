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

import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.unmarshallCall
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PKG = "com.example"

/**
 * The checks that keep the calls inside the rule directory. The file operations need a real
 * libcore, so `IfwStoreDeviceTest` covers them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IfwFileCallsTest {

    @Test
    fun givenNameOutsideTheDirectory_whenCallCreated_thenRejected() {
        val names = listOf("", "../etc", "a/b", "..", ".", ".hidden", "com.example\u0000")

        names.forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) { ReadIfwRuleFile(name) }
            assertThrows(name, IllegalArgumentException::class.java) { ReplaceIfwRuleFile(name, "<rules/>") }
            assertThrows(name, IllegalArgumentException::class.java) { DeleteIfwRuleFile(name) }
        }
    }

    @Test
    fun givenFileNotNamedAfterAPackage_whenCallCreated_thenAccepted() {
        // The calls guard the file access only; which files are packages' is the caller's decision.
        listOf("my-rules", "my rules", "1app", "custom..rules").forEach { name ->
            assertThat(ReadIfwRuleFile(name).packageName).isEqualTo(name)
            assertThat(DeleteIfwRuleFile(name).packageName).isEqualTo(name)
        }
    }

    @Test
    fun givenWakeFeatureFileName_whenCallCreated_thenRejected() {
        listOf("blocker-managed-wake", "blocker-wake-capability-probe", "blocker-managed-wake-com.other").forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) { ReplaceIfwRuleFile(name, "<rules/>") }
        }
    }

    @Test
    fun givenCalls_whenMarshalled_thenSameCallsArrive() {
        val calls = listOf(ReadIfwRuleFile(PKG), ReplaceIfwRuleFile(PKG, "<rules />"), DeleteIfwRuleFile(PKG), ListIfwRuleFiles)

        calls.forEach { assertThat(unmarshallCall(marshallCall(it))).isEqualTo(it) }
        assertThat(calls.all { it.needsPrivilegedProcess }).isTrue()
    }

    @Test
    fun givenForgedPathInParcel_whenUnmarshalledInPrivilegedProcess_thenRejected() {
        val parcel = Parcel.obtain()
        val bytes = try {
            parcel.writeString(ReadIfwRuleFile::class.java.name)
            parcel.writeString("../../../data/misc/secret")
            parcel.marshall()
        } finally {
            parcel.recycle()
        }

        assertThrows(IllegalArgumentException::class.java) { unmarshallCall(bytes) }
    }
}
