// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.test.repository

import com.merxury.blocker.core.data.repository.catalog.CatalogInstallResult
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.model.CatalogState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import javax.inject.Inject

/** A catalog that is always installed and has no icons, so tests never import the bundled seed. */
class FakeCatalogRepository @Inject constructor() : CatalogRepository {
    override val catalogState: Flow<CatalogState?> = flowOf(INSTALLED)

    override suspend fun installBundled(): CatalogInstallResult = CatalogInstallResult.SkippedNotNewer

    override suspend fun iconFile(path: String): File? = null

    private companion object {
        val INSTALLED = CatalogState(
            format = 2,
            sequence = 1,
            sourceCommit = "fake",
            publishedAt = "1970-01-01T00:00:00Z",
        )
    }
}
