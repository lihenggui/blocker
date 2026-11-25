// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.data.repository.userdata.resolveLibDisplayLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun UserDataRepository.catalogLanguage(): Flow<String> = userData
    .map { resolveLibDisplayLanguage(it.libDisplayLanguage) }
    .distinctUntilChanged()

internal inline fun <T> resolveCatalogText(selected: T?, english: T?, field: (T) -> String?): String? = selected?.let(field) ?: english?.let(field)
