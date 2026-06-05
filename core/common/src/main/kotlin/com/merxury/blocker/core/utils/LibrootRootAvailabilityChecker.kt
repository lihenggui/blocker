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

package com.merxury.blocker.core.utils

import be.mygod.librootkotlinx.NoShellException
import com.merxury.blocker.core.root.BlockerRootSession
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrootRootAvailabilityChecker @Inject constructor(
    private val rootSession: BlockerRootSession,
) : RootAvailabilityChecker {

    private val rooted = AtomicBoolean(false)

    override suspend fun isRootAvailable(): Boolean {
        if (rooted.get()) {
            return true
        }
        return try {
            rootSession.use { }
            rooted.set(true)
            Timber.i("Root server acquired")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoShellException) {
            Timber.e(e, "Root shell unavailable")
            false
        } catch (e: Exception) {
            Timber.e(e, "Root unavailable")
            false
        }
    }
}
