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

package com.merxury.blocker.navigation

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import kotlinx.serialization.json.Json
import org.junit.Test

/** Back stacks are saved by serializing their keys, so a saved key must keep decoding. */
class NavKeySerializationTest {

    @Test
    fun givenDetailKeySavedWithoutUser_whenDecoded_thenOpensTheOwnUser() {
        val key = Json.decodeFromString<AppDetailNavKey>("""{"packageName":"com.example"}""")

        assertThat(key).isEqualTo(AppDetailNavKey("com.example"))
        assertThat(key.userId).isNull()
    }

    @Test
    fun givenDetailKeyForAnotherUser_whenSavedAndRestored_thenKeepsTheUser() {
        val key = AppDetailNavKey("com.example", userId = 10)

        val restored = Json.decodeFromString<AppDetailNavKey>(Json.encodeToString(key))

        assertThat(restored).isEqualTo(key)
    }

    @Test
    fun givenAppListKey_whenSavedAndRestored_thenSameKey() {
        val restored = Json.decodeFromString<AppListNavKey>(Json.encodeToString(AppListNavKey))

        assertThat(restored).isSameInstanceAs(AppListNavKey)
    }
}
