// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model

data class CatalogState(
    val format: Int,
    val sequence: Long,
    val sourceCommit: String,
    val publishedAt: String,
)
