// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.repository.catalog.CatalogInstallResult
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult
import com.merxury.blocker.core.model.CatalogState
import com.merxury.blocker.core.model.preference.RuleServerProvider
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

    val updateRequests = mutableListOf<RuleServerProvider>()
    var updateResult: CatalogUpdateResult = CatalogUpdateResult.UpToDate

    override suspend fun update(provider: RuleServerProvider): CatalogUpdateResult {
        updateRequests.add(provider)
        return updateResult.also { if (it is CatalogUpdateResult.Updated) state.value = it.state }
    }

    override suspend fun installBundled(): CatalogInstallResult {
        state.value = installed
        return CatalogInstallResult.Installed(installed)
    }

    override suspend fun iconFile(path: String, sequence: Long?): File? = null
}
