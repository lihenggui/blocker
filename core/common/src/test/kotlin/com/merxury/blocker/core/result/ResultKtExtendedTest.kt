/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.result

import app.cash.turbine.test
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class ResultKtExtendedTest {

    @Test
    fun givenEmptyFlow_whenAsResult_thenEmitsLoadingAndCompletes() = runTest {
        emptyFlow<Int>()
            .asResult()
            .test {
                assertEquals(Result.Loading, awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun givenMultipleItemsFlow_whenAsResult_thenEmitsLoadingFollowedBySuccessItems() = runTest {
        flowOf(1, 2, 3)
            .asResult()
            .test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(1), awaitItem())
                assertEquals(Result.Success(2), awaitItem())
                assertEquals(Result.Success(3), awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun givenSingleItemFlow_whenAsResult_thenEmitsLoadingSuccessAndCompletes() = runTest {
        flowOf("hello")
            .asResult()
            .test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success("hello"), awaitItem())
                awaitComplete()
            }
    }
}
