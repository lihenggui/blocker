// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.repository.catalog.CatalogInstallResult
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.model.CatalogState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TestCatalogRepository : CatalogRepository {
    private val installed = CatalogState(2, 1, "test", "2026-01-01T00:00:00Z")
    private val state = MutableStateFlow<CatalogState?>(installed)
    override val catalogState = state.asStateFlow()

    fun clear() {
        state.value = null
    }

    override suspend fun installBundled(): CatalogInstallResult {
        state.value = installed
        return CatalogInstallResult.Installed(installed)
    }

    override suspend fun iconFile(path: String, sequence: Long?): File? = null
}
