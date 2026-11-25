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
import org.junit.Test

class AndroidUidTest {
    @Test
    fun givenUidsAcrossProfiles_whenCheckingAppRange_thenOnlyRegularAppIdsAreAccepted() {
        listOf(0, 10).forEach { user ->
            val offset = user * UIDS_PER_USER
            assertThat(isRegularAppUid(offset + 9_999)).isFalse()
            assertThat(isRegularAppUid(offset + 10_000)).isTrue()
            assertThat(isRegularAppUid(offset + 19_999)).isTrue()
            assertThat(isRegularAppUid(offset + 20_000)).isFalse()
        }
        assertThat(isRegularAppUid(-10_000)).isFalse()
    }

    @Test
    fun givenSameAppInTwoProfiles_whenDecodingUids_thenAppIdMatchesAndUserIdsDiffer() {
        val ownerUid = 10_456
        val workUid = 1_010_456

        assertThat(userOfUid(ownerUid)).isEqualTo(0)
        assertThat(userOfUid(workUid)).isEqualTo(10)
        assertThat(appIdOfUid(ownerUid)).isEqualTo(10_456)
        assertThat(appIdOfUid(workUid)).isEqualTo(10_456)
    }
}
