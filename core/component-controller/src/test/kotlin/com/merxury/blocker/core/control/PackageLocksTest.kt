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

package com.merxury.blocker.core.control

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PackageLocksTest {

    private val locks = PackageLocks()

    @Test
    fun givenPackageHeld_whenSamePackageLocked_thenRunsOnlyAfterRelease() = runTest {
        val held = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = launch {
            locks.withLock("com.example") {
                held.await()
                order += "first"
            }
        }
        runCurrent()
        val second = async { locks.withLock("com.example") { order += "second" } }
        runCurrent()
        assertThat(second.isActive).isTrue()

        held.complete(Unit)
        first.join()
        second.await()

        assertThat(order).containsExactly("first", "second").inOrder()
    }

    @Test
    fun givenPackageHeld_whenOtherPackageLocked_thenRunsAtOnce() = runTest {
        val held = CompletableDeferred<Unit>()
        val first = launch { locks.withLock("com.example") { held.await() } }
        runCurrent()

        assertThat(locks.withLock("com.other") { "ran" }).isEqualTo("ran")

        held.complete(Unit)
        first.join()
    }

    @Test
    fun givenBlockThrows_whenLocked_thenLockReleased() = runTest {
        runCatching { locks.withLock("com.example") { error("failed") } }

        assertThat(locks.withLock("com.example") { "ran" }).isEqualTo("ran")
    }
}
