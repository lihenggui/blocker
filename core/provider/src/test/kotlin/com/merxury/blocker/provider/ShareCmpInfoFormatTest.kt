// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pins the JSON LibChecker exchanges with ComponentProvider. The provider uses the default
 * [Json] instance, which omits defaults, rejects unknown keys and writes no whitespace.
 */
class ShareCmpInfoFormatTest {

    @Test
    fun givenModel_whenEncoding_thenWritesCompactJsonWithAllThreeComponentFields() {
        val text = Json.encodeToString(
            ShareCmpInfo(
                pkg = "com.example.app",
                components = listOf(
                    ShareCmpInfo.Component(type = "ACTIVITY", name = "com.example.app.Main", block = false),
                    ShareCmpInfo.Component(type = "SERVICE", name = "com.example.app.Sync", block = true),
                ),
            ),
        )

        assertThat(text).isEqualTo(
            """{"pkg":"com.example.app","components":[""" +
                """{"type":"ACTIVITY","name":"com.example.app.Main","block":false},""" +
                """{"type":"SERVICE","name":"com.example.app.Sync","block":true}]}""",
        )
    }

    @Test
    fun givenGoldenJson_whenDecoding_thenFieldsMatch() {
        val info = Json.decodeFromString<ShareCmpInfo>(
            """{"pkg":"com.example.app","components":[{"type":"RECEIVER","name":"com.example.app.Boot","block":true}]}""",
        )

        assertThat(info).isEqualTo(
            ShareCmpInfo(
                pkg = "com.example.app",
                components = listOf(ShareCmpInfo.Component("RECEIVER", "com.example.app.Boot", true)),
            ),
        )
    }

    @Test
    fun givenFieldsWithoutDefaults_whenEncodingFalseBlock_thenBlockIsStillWritten() {
        val text = Json.encodeToString(ShareCmpInfo.Component(type = "t", name = "n", block = false))

        assertThat(text).isEqualTo("""{"type":"t","name":"n","block":false}""")
    }

    @Test
    fun givenMissingRequiredKey_whenDecoding_thenThrowsSerializationException() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<ShareCmpInfo>("""{"pkg":"com.example.app"}""")
        }
    }

    @Test
    fun givenUnknownKey_whenDecoding_thenThrowsSerializationException() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<ShareCmpInfo>("""{"pkg":"p","components":[],"version":2}""")
        }
    }
}
