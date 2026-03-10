/*
 * Copyright 2025 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.provider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class ShareCmpInfoTest {

    @Test
    fun serializeAndDeserialize_roundTrip() {
        val original = ShareCmpInfo(
            pkg = "com.example.app",
            components = listOf(
                ShareCmpInfo.Component(type = "ACTIVITY", name = "com.example.app.MainActivity", block = true),
                ShareCmpInfo.Component(type = "SERVICE", name = "com.example.app.BackgroundService", block = false),
            ),
        )
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<ShareCmpInfo>(json)
        assertEquals(original, deserialized)
    }

    @Test
    fun deserialize_fromJsonString() {
        val json = """
            {
                "pkg": "com.example.app",
                "components": [
                    {"type": "RECEIVER", "name": "com.example.app.BootReceiver", "block": true}
                ]
            }
        """.trimIndent()
        val result = Json.decodeFromString<ShareCmpInfo>(json)
        assertEquals("com.example.app", result.pkg)
        assertEquals(1, result.components.size)
        assertEquals("RECEIVER", result.components[0].type)
        assertEquals("com.example.app.BootReceiver", result.components[0].name)
        assertEquals(true, result.components[0].block)
    }

    @Test
    fun serialize_emptyComponents() {
        val info = ShareCmpInfo(pkg = "com.example.app", components = emptyList())
        val json = Json.encodeToString(info)
        val deserialized = Json.decodeFromString<ShareCmpInfo>(json)
        assertEquals(info, deserialized)
        assertEquals(0, deserialized.components.size)
    }
}
