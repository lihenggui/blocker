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

package com.merxury.blocker.core.privileged.root

import android.os.Parcel
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.toCallFailure
import kotlinx.coroutines.CancellationException

private const val OUTCOME_SUCCESS = 0
private const val OUTCOME_FAILURE = 1

private const val CHANNEL_UNAVAILABLE = 0
private const val NOT_PERMITTED = 1
private const val TARGET_NOT_FOUND = 2
private const val SYSTEM_ERROR = 3

/**
 * Runs [call] against [system] and returns its outcome as bytes for [decodeOutcome]: the result,
 * or the [CallFailure] what it threw maps to. Runs in the root process, whose exceptions cannot
 * all be sent back as they are.
 */
internal fun <R> encodeOutcome(call: PrivilegedCall<R>, system: SystemServices): ByteArray {
    val parcel = Parcel.obtain()
    try {
        try {
            val result = call.run(system)
            parcel.writeInt(OUTCOME_SUCCESS)
            call.writeResult(result, parcel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            parcel.setDataSize(0)
            parcel.setDataPosition(0)
            parcel.writeInt(OUTCOME_FAILURE)
            parcel.writeCallFailure(e.toCallFailure())
        }
        return parcel.marshall()
    } finally {
        parcel.recycle()
    }
}

/** Reads what [encodeOutcome] wrote for [call]. */
internal fun <R> decodeOutcome(call: PrivilegedCall<R>, bytes: ByteArray): CallResult<R> {
    val parcel = Parcel.obtain()
    try {
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        return when (val outcome = parcel.readInt()) {
            OUTCOME_SUCCESS -> CallResult.Success(call.readResult(parcel))
            OUTCOME_FAILURE -> CallResult.Failure(parcel.readCallFailure())
            else -> error("Unknown outcome $outcome of $call")
        }
    } finally {
        parcel.recycle()
    }
}

private fun Parcel.writeCallFailure(failure: CallFailure) {
    when (failure) {
        CallFailure.ChannelUnavailable -> writeInt(CHANNEL_UNAVAILABLE)

        is CallFailure.NotPermitted -> {
            writeInt(NOT_PERMITTED)
            writeString(failure.message)
        }

        is CallFailure.TargetNotFound -> {
            writeInt(TARGET_NOT_FOUND)
            writeString(failure.message)
        }

        is CallFailure.SystemError -> {
            writeInt(SYSTEM_ERROR)
            writeString(failure.message)
        }
    }
}

private fun Parcel.readCallFailure(): CallFailure = when (val kind = readInt()) {
    CHANNEL_UNAVAILABLE -> CallFailure.ChannelUnavailable
    NOT_PERMITTED -> CallFailure.NotPermitted(readString())
    TARGET_NOT_FOUND -> CallFailure.TargetNotFound(readString())
    SYSTEM_ERROR -> CallFailure.SystemError(readString())
    else -> error("Unknown failure $kind")
}
