// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.CatalogState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

interface CatalogRepository {
    val catalogState: Flow<CatalogState?>
    suspend fun installBundled(): CatalogInstallResult
    suspend fun iconFile(path: String): File?
}

sealed interface CatalogInstallResult {
    data class Installed(val state: CatalogState) : CatalogInstallResult
    data object SkippedNotNewer : CatalogInstallResult
    data class Rejected(val reason: String) : CatalogInstallResult
}

internal class LocalCatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val installer: CatalogInstaller,
    private val bundled: BundledCatalogSource,
    private val icons: CatalogIcons,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CatalogRepository {
    override val catalogState: Flow<CatalogState?> = dao.observeState().map { state ->
        state?.let { CatalogState(it.format, it.sequence, it.sourceCommit, it.publishedAt) }
    }

    override suspend fun installBundled(): CatalogInstallResult = installer.install(bundled)

    override suspend fun iconFile(path: String): File? = withContext(ioDispatcher) {
        dao.readState()?.let { icons.resolve(it.sequence, path) }
    }
}
