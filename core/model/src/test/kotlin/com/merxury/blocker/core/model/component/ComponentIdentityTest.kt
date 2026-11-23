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

package com.merxury.blocker.core.model.component

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ComponentIdentityTest {

    @Test
    fun givenNegativeValue_whenCreateUserId_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { UserId(-1) }
        assertThrows(IllegalArgumentException::class.java) { UserId(-2) }
    }

    @Test
    fun whenCreateUserIdForWorkProfile_thenValueKept() {
        assertThat(UserId(10).value).isEqualTo(10)
        assertThat(UserId.SYSTEM.value).isEqualTo(0)
    }

    @Test
    fun whenFlatten_thenPackageAndFullClassJoinedBySlash() {
        val id = ComponentId("com.example", "com.example.sync.SyncService")

        assertThat(id.flatten()).isEqualTo("com.example/com.example.sync.SyncService")
        assertThat(id.simpleName).isEqualTo("SyncService")
    }

    @Test
    fun givenFullClassName_whenUnflatten_thenRoundTrips() {
        val id = ComponentId("com.example", "org.library.Receiver")

        assertThat(ComponentId.unflatten(id.flatten())).isEqualTo(id)
    }

    @Test
    fun givenRelativeClassName_whenUnflatten_thenResolvedAgainstPackage() {
        assertThat(ComponentId.unflatten("com.example/.MainActivity"))
            .isEqualTo(ComponentId("com.example", "com.example.MainActivity"))
    }

    @Test
    fun givenInnerClassName_whenUnflatten_thenDollarKept() {
        assertThat(ComponentId.unflatten("com.example/com.example.Outer\$Inner"))
            .isEqualTo(ComponentId("com.example", "com.example.Outer\$Inner"))
    }

    @Test
    fun givenMalformedText_whenUnflatten_thenNull() {
        assertThat(ComponentId.unflatten("com.example")).isNull()
        assertThat(ComponentId.unflatten("/com.example.Main")).isNull()
        assertThat(ComponentId.unflatten("com.example/")).isNull()
        assertThat(ComponentId.unflatten("com.example/a/b")).isNull()
        assertThat(ComponentId.unflatten("")).isNull()
    }

    @Test
    fun givenRelativeOrBlankNames_whenCreateComponentId_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { ComponentId("com.example", ".Main") }
        assertThrows(IllegalArgumentException::class.java) { ComponentId("com.example", "") }
        assertThrows(IllegalArgumentException::class.java) { ComponentId(" ", "com.example.Main") }
        assertThrows(IllegalArgumentException::class.java) { ComponentId("com.example", "a/b") }
    }

    @Test
    fun givenSameComponentForTwoUsers_whenCompareKeys_thenDifferent() {
        val id = ComponentId("com.example", "com.example.Main")

        assertThat(ComponentKey(id, UserId(0))).isNotEqualTo(ComponentKey(id, UserId(10)))
        assertThat(ComponentKey(id, UserId(10)).id).isEqualTo(ComponentKey(id, UserId(0)).id)
    }

    @Test
    fun givenSamePackageForTwoUsers_whenCompareAppKeys_thenDifferent() {
        assertThat(AppKey("com.example", UserId(0))).isNotEqualTo(AppKey("com.example", UserId(10)))
        assertThrows(IllegalArgumentException::class.java) { AppKey("", UserId(0)) }
    }
}
