// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import kotlinx.coroutines.flow.Flow

/** Requests a wake collection and reconciliation. */
interface WakeRunRequester {
    /** Whether any one-time wake run is enqueued or running. */
    val isRunning: Flow<Boolean>

    fun requestRun()
}
