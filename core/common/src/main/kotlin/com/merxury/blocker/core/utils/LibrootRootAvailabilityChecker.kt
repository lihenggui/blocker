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

import android.content.Context
import be.mygod.librootkotlinx.NoShellException
import com.merxury.blocker.core.root.BlockerRootSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrootRootAvailabilityChecker @Inject constructor(
    private val rootSession: BlockerRootSession,
    @ApplicationContext private val context: Context,
) : RootAvailabilityChecker {

    private val preferences by lazy { context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE) }
    private val granted = MutableStateFlow(false)
    val rootGranted = granted.asStateFlow()
    private val lock = Mutex()

    override suspend fun isRootAvailable(): Boolean = lock.withLock {
        granted.value || probe()
    }

    // Only a successful normal root authorization permits a background reconnection.
    suspend fun hasBackgroundRoot(): Boolean = lock.withLock {
        (granted.value || preferences.getBoolean("previous_root_grant", false)) && probe()
    }

    private suspend fun probe(): Boolean {
        val available = try {
            rootSession.use { true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoShellException) {
            Timber.e(e, "Root shell unavailable")
            false
        } catch (e: Exception) {
            Timber.e(e, "Root unavailable")
            false
        }
        if (preferences.getBoolean("previous_root_grant", false) != available) {
            preferences.edit().putBoolean("previous_root_grant", available).apply()
        }
        granted.value = available
        return available
    }
}
