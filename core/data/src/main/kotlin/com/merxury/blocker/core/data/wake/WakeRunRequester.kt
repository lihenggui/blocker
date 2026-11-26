// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

/** Requests a wake collection and reconciliation. */
fun interface WakeRunRequester {
    fun requestRun()
}
