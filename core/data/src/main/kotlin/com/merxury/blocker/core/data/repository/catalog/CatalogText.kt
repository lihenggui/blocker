// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

internal inline fun <T> resolveCatalogText(selected: T?, english: T?, field: (T) -> String?): String? = selected?.let(field) ?: english?.let(field)
