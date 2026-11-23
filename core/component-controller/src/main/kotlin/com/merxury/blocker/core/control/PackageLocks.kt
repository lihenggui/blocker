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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One lock per package name, shared by everything in the process that reads and then changes a
 * package's components, so two such changes of the same package never interleave: a change the
 * user asked for and one the wake feature makes run one after the other. The lock covers every
 * user of the package. It is not reentrant; a block must not take the same package's lock again.
 */
@Singleton
class PackageLocks @Inject constructor() {

    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Runs [block] while holding [packageName]'s lock, waiting for it first. */
    suspend fun <T> withLock(packageName: String, block: suspend () -> T): T = locks.computeIfAbsent(packageName) { Mutex() }.withLock { block() }
}
