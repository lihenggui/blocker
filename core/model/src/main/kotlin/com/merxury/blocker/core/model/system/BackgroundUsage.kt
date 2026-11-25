// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.system

/** Android's "Allow background usage" for one app in one user. */
data class BackgroundUsage(
    /** Whether the app may run in the background: RUN_ANY_IN_BACKGROUND is not ignored. */
    val allowed: Boolean,
    /** Whether the setting is changeable. System power allowlist entries are protected. */
    val changeable: Boolean,
)
