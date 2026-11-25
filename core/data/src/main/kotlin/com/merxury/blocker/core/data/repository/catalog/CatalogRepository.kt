// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.CatalogState
import com.merxury.blocker.core.model.preference.RuleServerProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

interface CatalogRepository {
    val catalogState: Flow<CatalogState?>
    suspend fun installBundled(): CatalogInstallResult
    suspend fun update(provider: RuleServerProvider): CatalogUpdateResult

    /** Resolves an icon in the given rule snapshot, or in the active catalog when no sequence is supplied. */
    suspend fun iconFile(path: String, sequence: Long? = null): File?
}

sealed interface CatalogInstallResult {
    data class Installed(val state: CatalogState) : CatalogInstallResult
    data object SkippedNotNewer : CatalogInstallResult
    data class Rejected(val reason: String) : CatalogInstallResult
}

sealed interface CatalogUpdateResult {
    data class Updated(val state: CatalogState) : CatalogUpdateResult
    data object UpToDate : CatalogUpdateResult
    data class Failed(val reason: Reason, val detail: String) : CatalogUpdateResult

    enum class Reason { NETWORK, INVALID_DATA, LOCAL_STORAGE }
}

internal class LocalCatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val installer: CatalogInstaller,
    private val bundled: BundledCatalogSource,
    private val icons: CatalogIcons,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val downloader: CatalogDownloader,
) : CatalogRepository {
    override val catalogState: Flow<CatalogState?> = dao.observeState().map { state ->
        state?.let { CatalogState(it.format, it.sequence, it.sourceCommit, it.publishedAt) }
    }

    override suspend fun installBundled(): CatalogInstallResult = installer.install(bundled)

    override suspend fun update(provider: RuleServerProvider): CatalogUpdateResult = downloader.update(provider)

    override suspend fun iconFile(path: String, sequence: Long?): File? = withContext(ioDispatcher) {
        val installedSequence = sequence ?: dao.readState()?.sequence ?: return@withContext null
        icons.resolve(installedSequence, path)
    }
}
