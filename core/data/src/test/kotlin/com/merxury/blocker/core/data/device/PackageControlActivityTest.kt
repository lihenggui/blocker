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

package com.merxury.blocker.core.data.device

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PackageControlActivityTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenOverlappingControls_whenOneIsCancelled_thenRefreshWaitsForTheOther() = runTest {
        val activity = PackageControlActivity { testScheduler.currentTime }
        val release = CompletableDeferred<Unit>()
        val cancelledControl = launch {
            activity.duringControl(setOf("example.app")) { awaitCancellation() }
        }
        launch {
            activity.duringControl(setOf("example.app")) { release.await() }
        }
        runCurrent()
        val waitingRefresh = launch { activity.awaitIdle("example.app") }

        cancelledControl.cancelAndJoin()
        runCurrent()

        assertThat(activity.isActive("example.app")).isTrue()
        assertThat(waitingRefresh.isCompleted).isFalse()
        activity.awaitIdle("example.other")

        release.complete(Unit)
        waitingRefresh.join()

        assertThat(activity.isActive("example.app")).isFalse()
    }
}
