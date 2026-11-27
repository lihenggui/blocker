// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged

import android.os.BadParcelableException
import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallBytesTest {

    @Test
    fun givenInlineBytes_whenReadWithALimit_thenTheBoundIsEnforced() {
        val payload = byteArrayOf(1, 2, 3)

        CallBytes.request(payload).use { call ->
            assertThat(call.read(maxBytes = payload.size)).isEqualTo(payload)
            val failure = assertThrows(IOException::class.java) { call.read(maxBytes = payload.size - 1) }
            assertThat(failure).hasMessageThat().isEqualTo("More than 2 bytes")
        }
    }

    @Test
    fun givenInlineParcelWithNoBytes_whenUnparceled_thenItIsRejected() {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInt(0)
            parcel.writeByteArray(null)
            parcel.setDataPosition(0)

            val failure = assertThrows(BadParcelableException::class.java) { CallBytes.createFromParcel(parcel) }

            assertThat(failure).hasMessageThat().isEqualTo("Missing inline call bytes")
        } finally {
            parcel.recycle()
        }
    }
}
