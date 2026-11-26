// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes wake firewall changes, reconciliation and the sender filter probe. */
@Singleton
class WakeRuleLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }
}
