// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

import kotlinx.serialization.Serializable

@Serializable
data class WakeProtection(
    val pathId: String,
    val caller: String,
    val target: String,
    val component: String,
    val action: String,
    val proof: String,
    val disabledByUser: Boolean,
    val configured: Boolean,
    val method: String = "IFW",
    val callerRevision: String = "",
    val targetRevision: String = "",
    val targetSigners: String = "",
    val previousState: Int? = null,
    val pendingChange: Boolean = false,
)
