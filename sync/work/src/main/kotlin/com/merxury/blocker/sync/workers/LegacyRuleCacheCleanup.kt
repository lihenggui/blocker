// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

class LegacyRuleCacheCleanup @Inject constructor(
    @FilesDir private val filesDir: File,
    private val catalog: CatalogRepository,
    private val preferences: BlockerPreferencesDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun run(): Boolean = withContext(ioDispatcher) {
        if (catalog.catalogState.first() == null) return@withContext true

        try {
            preferences.clearLegacyRuleCursor()
            deleteCheckout(filesDir.canonicalFile.resolve("blocker-general-rules"))
            true
        } catch (e: IOException) {
            Timber.w(e, "Failed to clean up the legacy rule checkout")
            false
        }
    }

    private suspend fun deleteCheckout(file: File) {
        currentCoroutineContext().ensureActive()
        // The root's parent is canonical. Recurse only through real directories in the checkout.
        if (file.isDirectory && file.canonicalFile == file) {
            val children = file.listFiles() ?: throw IOException("Cannot list ${file.path}")
            for (child in children) deleteCheckout(child)
        }
        if (!file.delete() && file.parentFile?.list()?.contains(file.name) != false) {
            throw IOException("Cannot delete ${file.path}")
        }
    }
}
