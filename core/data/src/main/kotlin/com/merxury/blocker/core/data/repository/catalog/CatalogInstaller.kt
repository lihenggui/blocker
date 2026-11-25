// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import android.database.sqlite.SQLiteException
import com.merxury.blocker.core.database.catalog.CatalogComponentWithText
import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.database.catalog.CatalogRuleWithText
import com.merxury.blocker.core.database.catalog.CatalogStateEntity
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CatalogInstaller @Inject constructor(
    private val dao: CatalogDao,
    private val icons: CatalogIcons,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    suspend fun install(source: CatalogSource): CatalogInstallResult = withContext(ioDispatcher) {
        mutex.withLock {
            var sequence: Long? = null
            try {
                val manifestBytes = inCatalogFile(CATALOG_MANIFEST) { source.open(CATALOG_MANIFEST).use { it.readBytes() } }
                val manifest = CatalogManifest.parse(manifestBytes)
                sequence = manifest.state.sequence

                val active = dao.readState()
                existingInstall(manifest, active)?.let { result ->
                    if (active != null) icons.deleteOlderThan(active.sequence)
                    return@withLock result
                }
                manifest.verifyPaths(source.paths())
                val rules = source.readVerified(manifest.files.getValue(CATALOG_RULES)) {
                    readCatalogRules(it, manifest.files.keys)
                }
                installNew(source, manifest, manifestBytes, rules)
            } catch (e: IOException) {
                reject(sequence, e)
            } catch (e: SQLiteException) {
                reject(sequence, e)
            }
        }
    }

    private fun existingInstall(manifest: CatalogManifest, active: CatalogStateEntity?): CatalogInstallResult? {
        val sequence = manifest.state.sequence
        if (active == null || sequence > active.sequence) return null
        if (sequence == active.sequence) {
            val previous = icons.directory(sequence).resolve(CATALOG_MANIFEST).readBytes()
            verifyCatalog(CatalogManifest.parse(previous).files == manifest.files) {
                "Sequence $sequence has a different files list"
            }
        }
        return CatalogInstallResult.SkippedNotNewer
    }

    private suspend fun installNew(
        source: CatalogSource,
        manifest: CatalogManifest,
        manifestBytes: ByteArray,
        rules: List<CatalogRuleWithText>,
    ): CatalogInstallResult {
        val state = manifest.state
        val directory = icons.directory(state.sequence)
        try {
            icons.prepare(directory)
            writeIcons(source, manifest, directory)
            // Preserve the files list with the icons so equality checks survive a process restart.
            directory.resolve(CATALOG_MANIFEST).writeBytes(manifestBytes)
            return dao.writeTransaction {
                existingInstall(manifest, dao.readState())?.let { return@writeTransaction it }
                dao.clearAll()
                rules.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
                    dao.insertRules(batch.map { it.rule })
                    dao.insertRuleTexts(batch.flatMap { it.texts })
                }
                importComponents(source, manifest, rules.map { it.rule.id }.toSet())
                dao.insertState(CatalogStateEntity(state.format, state.sequence, state.sourceCommit, state.publishedAt))
                CatalogInstallResult.Installed(state)
            }
        } finally {
            // Cancellation can race the transaction's return. Read the committed state before
            // removing files so a committed catalog always keeps its icons.
            withContext(NonCancellable) { finishInstall(state.sequence, directory) }
        }
    }

    private suspend fun writeIcons(source: CatalogSource, manifest: CatalogManifest, directory: File) {
        for (file in manifest.files.values.filter { isIconPath(it.path) }) {
            currentCoroutineContext().ensureActive()
            val output = directory.resolve(file.path)
            val parent = checkNotNull(output.parentFile)
            if (!parent.isDirectory && !parent.mkdirs()) {
                throw IOException("Cannot create parent directory for ${file.path}")
            }
            source.readVerified(file) { input -> output.outputStream().buffered().use { input.copyTo(it) } }
        }
    }

    private suspend fun importComponents(source: CatalogSource, manifest: CatalogManifest, ruleIds: Set<Long>) {
        val batch = ArrayList<CatalogComponentWithText>(IMPORT_BATCH_SIZE)
        var previous: String? = null
        source.readVerified(manifest.files.getValue(CATALOG_COMPONENTS)) { input ->
            InputStreamReader(input, Charsets.UTF_8.newDecoder()).buffered().lineSequence().forEachIndexed { index, line ->
                currentCoroutineContext().ensureActive()
                val component = inCatalogFile("line ${index + 1}") {
                    val row = readCatalogComponent(line, ruleIds)
                    val name = row.component.name
                    verifyCatalog(previous?.precedes(name) != false) { "Component names must be sorted and unique" }
                    previous = name
                    row
                }
                batch.add(component)
                if (batch.size == IMPORT_BATCH_SIZE) insertComponentBatch(batch)
            }
            if (batch.isNotEmpty()) insertComponentBatch(batch)
        }
    }

    private suspend fun insertComponentBatch(batch: MutableList<CatalogComponentWithText>) {
        dao.insertComponents(batch.map { it.component })
        dao.insertComponentTexts(batch.flatMap { it.texts })
        batch.clear()
    }

    private suspend fun finishInstall(sequence: Long, directory: File) {
        val active = try {
            dao.readState()?.sequence
        } catch (e: SQLiteException) {
            // If commit status is unknown, the next install can clean up these files.
            Timber.w(e, "Cannot read catalog state while cleaning up sequence %d", sequence)
            return
        }
        if (active == sequence) icons.deleteOlderThan(sequence) else icons.delete(directory)
    }

    private fun reject(sequence: Long?, e: Exception): CatalogInstallResult.Rejected {
        Timber.w(e, "Catalog install failed for sequence %s", sequence ?: "unknown")
        return CatalogInstallResult.Rejected(e.message ?: e.javaClass.simpleName)
    }
}

private const val IMPORT_BATCH_SIZE = 400
