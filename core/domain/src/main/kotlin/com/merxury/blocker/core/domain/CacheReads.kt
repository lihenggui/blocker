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

package com.merxury.blocker.core.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits each value of [this] and then waits [periodMillis] before it takes the next. A source that
 * keeps only its latest value while it waits, as a Room query does with the changes to its tables,
 * is then read at most once per period, and its latest value still arrives.
 */
fun <T> Flow<T>.readAtMostEvery(periodMillis: Long): Flow<T> = flow {
    collect {
        emit(it)
        delay(periodMillis)
    }
}
