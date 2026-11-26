// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.database.catalog.CatalogDatabase
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.minutes

private const val ICON_PATH = "icon/example.svg"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogDownloaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var database: CatalogDatabase
    private lateinit var dao: CatalogDao
    private lateinit var icons: CatalogIcons
    private lateinit var installer: CatalogInstaller
    private val source = fixtureCatalog(2, iconPath = ICON_PATH)
    private val assets = mutableMapOf(
        MANIFEST_ASSET to source.files.getValue(CATALOG_MANIFEST),
        RULES_ASSET to gzip(source.files.getValue(CATALOG_RULES)),
        COMPONENTS_ASSET to gzip(source.files.getValue(CATALOG_COMPONENTS)),
        ICONS_ASSET to zip(ICON_PATH to source.files.getValue(ICON_PATH)),
    )
    private val requests = mutableListOf<String>()
    private val fetcher = CatalogHttpFetcher { url, consume ->
        val asset = url.substringAfterLast('/')
        requests.add(asset)
        val bytes = assets.getValue(asset)
        bytes.inputStream().use { consume(it, bytes.size.toLong()) }
    }

    @Before
    fun openDatabase() {
        cacheDir = temporaryFolder.newFolder("cache")
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        dao = database.catalogDao()
        icons = CatalogIcons(temporaryFolder.root)
        installer = CatalogInstaller(dao, icons, Dispatchers.IO)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun givenAnInstalledSequence_whenRemoteIsNoNewer_thenOnlyTheManifestIsRequested() = runTest {
        installer.install(fixtureCatalog(2))

        assertThat(downloader().update(GITHUB)).isEqualTo(CatalogUpdateResult.UpToDate)
        assertThat(requests).containsExactly(MANIFEST_ASSET)
        assertThat(cacheDir.list()).isEmpty()
    }

    @Test
    fun givenANewerRelease_whenDownloaded_thenTheInstallerPublishesIt() = runTest {
        installer.install(fixtureCatalog(1))

        val result = downloader().update(GITHUB) as CatalogUpdateResult.Updated

        assertThat(result.state.sequence).isEqualTo(2L)
        assertThat(dao.readState()?.sequence).isEqualTo(2L)
        assertThat(dao.findComponents(listOf(FIXTURE_COMPONENT_NAME))).hasSize(1)
        assertThat(icons.resolve(2, ICON_PATH)?.readText()).isEqualTo("old icon")
        assertThat(icons.directory(2).resolve(CATALOG_MANIFEST).readBytes()).isEqualTo(assets.getValue(MANIFEST_ASSET))
        assertThat(requests).containsExactly(MANIFEST_ASSET, RULES_ASSET, COMPONENTS_ASSET, ICONS_ASSET).inOrder()
        assertThat(cacheDir.list()).isEmpty()
    }

    @Test
    fun givenFormatThree_whenDownloaded_thenTheManifestIsRejected() = runTest {
        assets[MANIFEST_ASSET] = fixtureCatalog(3, format = 3).files.getValue(CATALOG_MANIFEST)

        assertInvalid("Unsupported catalog format 3")
        assertThat(requests).containsExactly(MANIFEST_ASSET)
    }

    @Test
    fun givenInvalidGzip_whenUnpacking_thenTheReleaseIsRejectedAndStagingDeleted() = runTest {
        assets[RULES_ASSET] = "not a gzip stream".encodeToByteArray()

        assertInvalid("Invalid archive $RULES_ASSET")
    }

    @Test
    fun givenAnOversizedGzipPayload_whenUnpacking_thenTheReleaseIsRejected() = runTest {
        assets[RULES_ASSET] = gzip(source.files.getValue(CATALOG_RULES) + 0.toByte())

        assertInvalid("$CATALOG_RULES exceeds")
    }

    @Test
    fun givenAnOversizedIcon_whenUnpacking_thenTheReleaseIsRejected() = runTest {
        assets[ICONS_ASSET] = zip(ICON_PATH to (source.files.getValue(ICON_PATH) + 0.toByte()))

        assertInvalid("$ICON_PATH exceeds")
    }

    @Test
    fun givenAnOversizedContentLength_whenDownloading_thenTheAssetIsRejectedBeforeReading() = runTest {
        val download = downloader { url, consume ->
            if (url.endsWith(RULES_ASSET)) {
                val stream = object : InputStream() {
                    override fun read(): Int = error("Oversized response was read")
                }
                consume(stream, MAX_ASSET_BYTES + 1)
            } else {
                fetcher.fetch(url, consume)
            }
        }

        assertInvalid("$RULES_ASSET exceeds", download)
    }

    @Test
    fun givenAnUnboundedResponse_whenDownloading_thenOnlyOneBytePastTheAssetLimitIsRead() = runTest {
        var bytesRead = 0L
        val download = downloader { url, consume ->
            if (url.endsWith(RULES_ASSET)) {
                val stream = object : InputStream() {
                    override fun read(): Int = error("Expected buffered reads")

                    override fun read(output: ByteArray, offset: Int, length: Int): Int {
                        bytesRead += length
                        return length
                    }
                }
                consume(stream, -1)
            } else {
                fetcher.fetch(url, consume)
            }
        }

        assertInvalid("$RULES_ASSET exceeds", download)
        assertThat(bytesRead).isEqualTo(MAX_ASSET_BYTES + 1)
    }

    @Test
    fun givenUnsafeZipPaths_whenUnpacking_thenTheReleaseIsRejected() = runTest {
        for (path in listOf("../escape", "/icon/example.svg", "icon/../escape", "icon/nested/example.svg", "icon/subdir/")) {
            assets[ICONS_ASSET] = zip(path to byteArrayOf())

            assertInvalid("ZIP entry")
        }
        assertThat(temporaryFolder.root.resolve("escape").exists()).isFalse()
    }

    @Test
    fun givenAnUnlistedIcon_whenUnpacking_thenTheReleaseIsRejected() = runTest {
        assets[ICONS_ASSET] = zip("icon/unlisted.svg" to byteArrayOf())

        assertInvalid("Unexpected ZIP entry icon/unlisted.svg")
    }

    @Test
    fun givenDuplicateZipEntries_whenUnpacking_thenTheReleaseIsRejected() = runTest {
        val other = "icon/EXAMPLE.svg"
        val archive = zip(ICON_PATH to byteArrayOf(), other to byteArrayOf())
        // ZipOutputStream refuses duplicates, so rename the second entry in both headers.
        val duplicate = archive.toString(Charsets.ISO_8859_1).replace(other, ICON_PATH)
        assets[ICONS_ASSET] = duplicate.toByteArray(Charsets.ISO_8859_1)

        assertInvalid("Duplicate ZIP entry $ICON_PATH")
    }

    @Test
    fun givenAListedZipSymlink_whenUnpacking_thenTheReleaseIsRejected() = runTest {
        val archive = assets.getValue(ICONS_ASSET)
        val signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02).toList()
        val centralDirectory = archive.toList().windowed(4).indexOf(signature)
        archive[centralDirectory + 5] = 3 // Unix creator.
        archive[centralDirectory + 41] = 0xa0.toByte() // S_IFLNK in the Unix mode.

        assertInvalid("Non-regular ZIP entry $ICON_PATH")
    }

    @Test
    fun givenAWrongHash_whenInstalling_thenThePreviousCatalogSurvives() = runTest {
        installer.install(fixtureCatalog(1))
        assets[ICONS_ASSET] = zip(ICON_PATH to "bad icon".encodeToByteArray())

        val failure = downloader().update(GITHUB) as CatalogUpdateResult.Failed

        assertThat(failure.reason).isEqualTo(CatalogUpdateResult.Reason.INVALID_DATA)
        assertThat(failure.detail).contains("SHA-256 mismatch")
        assertThat(dao.readState()?.sequence).isEqualTo(1L)
        assertThat(cacheDir.list()).isEmpty()
    }

    @Test
    fun givenANetworkFailureAfterTheManifest_whenDownloading_thenItCanRetryAndRemovesStaging() = runTest {
        val download = downloader { url, consume ->
            if (url.endsWith(RULES_ASSET)) throw CatalogNetworkException("Connection dropped")
            fetcher.fetch(url, consume)
        }

        val failure = download.update(GITHUB) as CatalogUpdateResult.Failed

        assertThat(failure.reason).isEqualTo(CatalogUpdateResult.Reason.NETWORK)
        assertThat(failure.detail).contains(RULES_ASSET)
        assertThat(cacheDir.list()).isEmpty()
    }

    @Test
    fun givenAnAssetIsDownloading_whenCancelled_thenStagingIsRemoved() = runTest {
        val started = CompletableDeferred<Unit>()
        val download = downloader { url, consume ->
            if (url.endsWith(RULES_ASSET)) {
                started.complete(Unit)
                awaitCancellation()
            }
            fetcher.fetch(url, consume)
        }

        val job = launch { download.update(GITHUB) }
        started.await()
        assertThat(cacheDir.list()).hasLength(1)
        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(cacheDir.list()).isEmpty()
        assertThat(dao.readState()).isNull()
    }

    @Test
    fun givenReleaseRedirects_whenDownloadingWithOkHttp_thenTheInstallerPublishesTheCatalog() = runTest {
        val paths = ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.use {
                val path = it.requestURI.path
                paths.add(path)
                val name = path.substringAfterLast('/')
                if (path.startsWith("/release/")) {
                    it.responseHeaders.set("Location", "/asset/$name?signature=example")
                    it.sendResponseHeaders(302, -1)
                } else {
                    val bytes = assets.getValue(name)
                    it.sendResponseHeaders(200, bytes.size.toLong())
                    it.responseBody.write(bytes)
                }
            }
        }
        server.start()
        try {
            val http = OkHttpCatalogFetcher(OkHttpClient())
            val download = downloader { url, consume ->
                http.fetch("http://127.0.0.1:${server.address.port}/release/${url.substringAfterLast('/')}", consume)
            }

            assertThat(download.update(GITHUB)).isInstanceOf(CatalogUpdateResult.Updated::class.java)
            assertThat(paths).containsExactlyElementsIn(
                assets.keys.flatMap { listOf("/release/$it", "/asset/$it") },
            ).inOrder()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun givenPublishedReleaseOne_whenDownloadedFromBothMirrors_thenBothInstall() = runTest(timeout = 4.minutes) {
        assumeTrue(System.getenv("BLOCKER_LIVE_CATALOG_CHECK") == "1")
        val download = downloader(OkHttpCatalogFetcher(OkHttpClient()))

        for (provider in RuleServerProvider.entries) {
            val result = download.update(provider)
            assertThat(result).isInstanceOf(CatalogUpdateResult.Updated::class.java)
            assertThat((result as CatalogUpdateResult.Updated).state.sequence).isEqualTo(1L)
            assertThat(dao.readRules()).hasSize(512)
            assertThat(cacheDir.list()).isEmpty()
            println("$provider installed sequence ${result.state.sequence} from ${result.state.sourceCommit}")
            dao.clearAll()
        }
    }

    private fun downloader(fetcher: CatalogHttpFetcher = this.fetcher) = CatalogDownloader(cacheDir, dao, installer, Dispatchers.IO, fetcher)

    private suspend fun assertInvalid(detail: String, downloader: CatalogDownloader = downloader()) {
        val failure = downloader.update(GITHUB) as CatalogUpdateResult.Failed
        assertThat(failure.reason).isEqualTo(CatalogUpdateResult.Reason.INVALID_DATA)
        assertThat(failure.detail).contains(detail)
        assertThat(dao.readState()).isNull()
        assertThat(cacheDir.list()).isEmpty()
    }
}

private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
    GZIPOutputStream(this).use { it.write(bytes) }
}.toByteArray()

private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().apply {
    ZipOutputStream(this).use { zip ->
        for ((path, bytes) in entries) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}.toByteArray()
