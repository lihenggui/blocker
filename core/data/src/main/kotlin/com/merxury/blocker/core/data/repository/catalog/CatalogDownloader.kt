// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import android.database.sqlite.SQLiteException
import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.di.CacheDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.preference.RuleServerProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

internal const val MANIFEST_ASSET = "v2-catalog.json"
internal const val RULES_ASSET = "v2-rules.json.gz"
internal const val COMPONENTS_ASSET = "v2-components.jsonl.gz"
internal const val ICONS_ASSET = "v2-icons.zip"

private const val MAX_MANIFEST_BYTES = 1024L * 1024
internal const val MAX_ASSET_BYTES = 64L * 1024 * 1024

internal class CatalogDownloader @Inject constructor(
    @CacheDir private val cacheDir: File,
    private val dao: CatalogDao,
    private val installer: CatalogInstaller,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val fetcher: CatalogHttpFetcher,
) {
    suspend fun update(provider: RuleServerProvider): CatalogUpdateResult = withContext(ioDispatcher) {
        try {
            val manifestBytes = ByteArrayOutputStream().apply {
                download(provider, MANIFEST_ASSET, this, MAX_MANIFEST_BYTES)
            }.toByteArray()
            val manifest = CatalogManifest.parse(manifestBytes)
            val installed = dao.readState()
            if (installed != null && manifest.state.sequence <= installed.sequence) {
                return@withContext CatalogUpdateResult.UpToDate
            }
            stageAndInstall(provider, manifest, manifestBytes)
        } catch (e: CatalogNetworkException) {
            ensureActive()
            Timber.w("Catalog download from %s failed: %s", provider.name, e.message)
            CatalogUpdateResult.Failed(CatalogUpdateResult.Reason.NETWORK, e.message.orEmpty())
        } catch (e: InvalidCatalog) {
            ensureActive()
            Timber.w("Invalid catalog release from %s", provider.name)
            CatalogUpdateResult.Failed(CatalogUpdateResult.Reason.INVALID_DATA, e.message.orEmpty())
        } catch (e: IOException) {
            ensureActive()
            Timber.w(e, "Cannot stage catalog release from %s", provider.name)
            CatalogUpdateResult.Failed(CatalogUpdateResult.Reason.LOCAL_STORAGE, e.message.orEmpty())
        } catch (e: SQLiteException) {
            ensureActive()
            Timber.w(e, "Cannot read installed catalog for update from %s", provider.name)
            CatalogUpdateResult.Failed(CatalogUpdateResult.Reason.LOCAL_STORAGE, e.message.orEmpty())
        }
    }

    private suspend fun stageAndInstall(
        provider: RuleServerProvider,
        manifest: CatalogManifest,
        manifestBytes: ByteArray,
    ): CatalogUpdateResult {
        val stage = File.createTempFile("catalog-", ".stage", cacheDir)
        try {
            if (!stage.delete() || !stage.mkdir()) throw IOException("Cannot create catalog staging directory")
            val payload = stage.resolve("payload")
            if (!payload.mkdir()) throw IOException("Cannot create catalog payload directory")
            payload.resolve(CATALOG_MANIFEST).writeBytes(manifestBytes)

            // Reuse the scratch file so only one compressed asset occupies disk at a time.
            val archive = stage.resolve("asset")
            stageAsset(provider, RULES_ASSET, archive) {
                unpackGzip(archive, payload, manifest.files.getValue(CATALOG_RULES))
            }
            stageAsset(provider, COMPONENTS_ASSET, archive) {
                unpackGzip(archive, payload, manifest.files.getValue(CATALOG_COMPONENTS))
            }
            stageAsset(provider, ICONS_ASSET, archive) {
                unpackCatalogIcons(archive, payload, manifest, currentCoroutineContext())
            }

            return when (val result = installer.install(DirectoryCatalogSource(payload))) {
                is CatalogInstallResult.Installed -> CatalogUpdateResult.Updated(result.state)

                CatalogInstallResult.SkippedNotNewer -> CatalogUpdateResult.UpToDate

                is CatalogInstallResult.Rejected -> CatalogUpdateResult.Failed(
                    CatalogUpdateResult.Reason.INVALID_DATA,
                    result.reason,
                )
            }
        } finally {
            if (!stage.deleteRecursively()) Timber.w("Cannot remove catalog staging directory %s", stage.name)
        }
    }

    private suspend inline fun stageAsset(
        provider: RuleServerProvider,
        asset: String,
        archive: File,
        unpack: () -> Unit,
    ) {
        archive.outputStream().buffered().use { download(provider, asset, it, MAX_ASSET_BYTES) }
        try {
            unpack()
        } catch (e: ZipException) {
            throw InvalidCatalog("Invalid archive $asset", e)
        } catch (e: EOFException) {
            throw InvalidCatalog("Truncated archive $asset", e)
        }
    }

    private suspend fun download(provider: RuleServerProvider, asset: String, output: OutputStream, limit: Long) {
        val context = currentCoroutineContext()
        try {
            fetcher.fetch("${provider.releaseBaseUrl}/$asset") { input, length ->
                verifyCatalog(length <= limit) { "$asset exceeds $limit bytes" }
                input.copyCatalogBytes(output, limit, asset, context)
            }
        } catch (e: CatalogNetworkException) {
            throw CatalogNetworkException("$asset: ${e.message}")
        } catch (e: InvalidCatalog) {
            throw InvalidCatalog("$asset: ${e.message}", e)
        }
    }

    private suspend fun unpackGzip(archive: File, payload: File, file: CatalogFile) {
        val context = currentCoroutineContext()
        archive.inputStream().buffered().use { compressed ->
            GZIPInputStream(compressed).use { input ->
                payload.resolve(file.path).outputStream().buffered().use { output ->
                    input.copyCatalogBytes(output, file.size, file.path, context)
                }
            }
        }
    }
}

internal fun InputStream.copyCatalogBytes(output: OutputStream, limit: Long, name: String, context: CoroutineContext) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = limit
    while (true) {
        context.ensureActive()
        // Read at most one byte beyond the limit, without overflowing a Long-sized manifest value.
        val length = if (remaining < buffer.size) remaining.toInt() + 1 else buffer.size
        val read = read(buffer, 0, length)
        if (read == -1) return
        verifyCatalog(read <= remaining) { "$name exceeds $limit bytes" }
        output.write(buffer, 0, read)
        remaining -= read
    }
}
