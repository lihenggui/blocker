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

package com.merxury.core.ifw

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class WakeFilterProbeTest {
    private val writes = mutableListOf<Pair<WakeRuleFileKind, String?>>()

    @Test fun givenProbeWriteFails_whenTestingPeerFilter_thenProbeDeletedAnyway() = runTest {
        val writer = object : WakeRuleWriter {
            override suspend fun write(kind: WakeRuleFileKind, xml: String?) {
                writes += kind to xml
                if (xml != null) throw IOException("Cannot write the wake PROBE rules: ChannelUnavailable")
            }

            override suspend fun removeTarget(kind: WakeRuleFileKind, packageName: String) = error("Unexpected target removal")

            override suspend fun reset(packageName: String?) = error("Unexpected reset")
        }

        val thrown = runCatching { testPeerFilter(RuntimeEnvironment.getApplication(), writer) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(writes.map { it.first }).containsExactly(WakeRuleFileKind.PROBE, WakeRuleFileKind.PROBE).inOrder()
        assertThat(writes.first().second).contains("PRIVATE_IFW_PROBE")
        assertThat(writes.last().second).isNull()
    }
}
