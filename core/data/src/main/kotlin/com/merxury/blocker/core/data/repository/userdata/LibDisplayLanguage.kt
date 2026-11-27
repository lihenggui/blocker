// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.userdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun UserDataRepository.observeLibDisplayLanguage(): Flow<String> = userData
    .map { resolveLibDisplayLanguage(it.libDisplayLanguage) }
    .distinctUntilChanged()
