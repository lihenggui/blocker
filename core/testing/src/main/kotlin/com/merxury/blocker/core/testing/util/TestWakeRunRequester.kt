// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.util

import com.merxury.blocker.core.data.wake.WakeRunRequester
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestWakeRunRequester @Inject constructor() : WakeRunRequester {
    var requests = 0
        private set

    override fun requestRun() {
        requests++
    }
}
