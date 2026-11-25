// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.database.catalog.CatalogDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource.Monotonic

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogInstallerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var database: CatalogDatabase
    private lateinit var dao: CatalogDao
    private lateinit var installer: CatalogInstaller
    private lateinit var icons: CatalogIcons
    private lateinit var bundled: BundledCatalogSource
    private lateinit var repository: CatalogRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        dao = database.catalogDao()
        icons = CatalogIcons(temporaryFolder.root)
        installer = CatalogInstaller(dao, icons, Dispatchers.IO)
        bundled = BundledCatalogSource(context)
        repository = LocalCatalogRepository(dao, installer, bundled, icons, Dispatchers.IO)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun givenAnEmptyDatabase_whenInstalling_thenTheFilesAreReadOnceAndDataIsPublished() = runTest {
        val source = fixtureCatalog(1)
        assertThat(repository.catalogState.first()).isNull()
        assertThat(repository.iconFile(FIXTURE_ICON)).isNull()

        val result = installer.install(source) as CatalogInstallResult.Installed

        assertThat(source.opens).containsExactlyEntriesIn(source.files.keys.associateWith { 1 })
        assertThat(result.state.sequence).isEqualTo(1L)
        assertThat(repository.catalogState.first()).isEqualTo(result.state)
        val rule = dao.readRules().single()
        assertThat(rule.rule.keywords).containsExactly("second", "first").inOrder()
        assertThat(rule.texts.map { it.language }).containsExactly("en", "zh-CN")
        val component = dao.findComponents(listOf(FIXTURE_COMPONENT_NAME)).single()
        assertThat(component.component.sdkRuleId).isEqualTo(1L)
        assertThat(component.texts.single().description).isEmpty()
        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("old icon")
        assertThat(repository.iconFile("icon/../catalog.json")).isNull()
        assertThat(repository.iconFile("catalog.json")).isNull()
    }

    @Test
    fun givenAnInstalledCatalog_whenInstallingALowerSequence_thenOnlyTheManifestIsRead() = runTest {
        installer.install(fixtureCatalog(2))
        val older = fixtureCatalog(1)

        assertThat(installer.install(older)).isEqualTo(CatalogInstallResult.SkippedNotNewer)
        assertThat(older.opens).containsExactly(CATALOG_MANIFEST, 1)
        assertThat(dao.readState()?.sequence).isEqualTo(2L)
    }

    @Test
    fun givenAnInstalledCatalog_whenReinstallingTheSameFilesAfterRestart_thenOnlyTheManifestIsRead() = runTest {
        installer.install(fixtureCatalog(1))
        val same = fixtureCatalog(1, sourceCommit = "different metadata")
        val restarted = CatalogInstaller(dao, icons, Dispatchers.IO)

        assertThat(restarted.install(same)).isEqualTo(CatalogInstallResult.SkippedNotNewer)
        assertThat(same.opens).containsExactly(CATALOG_MANIFEST, 1)
        assertThat(dao.readState()?.sourceCommit).isEqualTo("fixture")
    }

    @Test
    fun givenLostIconFiles_whenReinstallingTheSameSequence_thenTheIconsAreRestored() = runTest {
        installer.install(fixtureCatalog(1))
        icons.delete(icons.directory(1))

        installer.install(fixtureCatalog(1))

        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("old icon")
        assertThat(dao.readState()?.sequence).isEqualTo(1L)
    }

    @Test
    fun givenAnInstalledCatalog_whenAnEqualSequenceListsDifferentFiles_thenItIsRejected() = runTest {
        installer.install(fixtureCatalog(1))

        assertRejected(fixtureCatalog(1, icon = "new icon"), "different files list")
        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("old icon")
    }

    @Test
    fun givenAChangedIconWithTheSameSize_whenInstalling_thenTheHashMismatchRejectsTheCatalog() = runTest {
        val source = fixtureCatalog(1)
        source.files[FIXTURE_ICON] = "new icon".encodeToByteArray()

        assertRejected(source, "SHA-256 mismatch")
        assertThat(dao.readState()).isNull()
        assertThat(icons.directory(1).exists()).isFalse()
    }

    @Test
    fun givenAFileWithTheWrongSize_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val source = fixtureCatalog(1)
        source.files[CATALOG_COMPONENTS] = ByteArray(0)

        assertRejected(source, "Size mismatch")
        assertThat(dao.readRules()).isEmpty()
    }

    @Test
    fun givenAMissingFile_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val source = fixtureCatalog(1)
        source.files.remove(FIXTURE_ICON)

        assertRejected(source, "Missing file $FIXTURE_ICON")
    }

    @Test
    fun givenAnExtraFile_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val source = fixtureCatalog(1)
        source.files["icon/extra.svg"] = "extra".encodeToByteArray()

        assertRejected(source, "Unknown file icon/extra.svg")
    }

    @Test
    fun givenAnUnknownManifestPath_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val source = fixtureCatalog(1)
        val manifest = source.files.getValue(CATALOG_MANIFEST).decodeToString()
        source.files[CATALOG_MANIFEST] = manifest.replace(FIXTURE_ICON, "icon/../extra.svg").encodeToByteArray()

        assertRejected(source, "Unknown file icon/../extra.svg")
    }

    @Test
    fun givenARuleWithAnUnlistedIcon_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val rules = FIXTURE_RULES.replace(FIXTURE_ICON, "icon/unlisted.svg")
        assertRejected(fixtureCatalog(1, rules = rules), "unlisted icon icon/unlisted.svg")
    }

    @Test
    fun givenAnUnknownSdkRuleId_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val component = FIXTURE_COMPONENT.replace("\"sdkRuleId\":1", "\"sdkRuleId\":2")
        assertRejected(fixtureCatalog(1, components = "$component\n"), "unknown sdkRuleId 2")
    }

    @Test
    fun givenAComponentWithoutEnglishText_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val component = FIXTURE_COMPONENT.replace("\"en\"", "\"zh-CN\"")
        assertRejected(fixtureCatalog(1, components = "$component\n"), "Missing en text")
    }

    @Test
    fun givenARuleWithoutAnEnglishName_whenInstalling_thenTheCatalogIsRejected() = runTest {
        val rules = FIXTURE_RULES.replace("\"name\":\"Example\"", "\"company\":\"Example\"")
        assertRejected(fixtureCatalog(1, rules = rules), "requires a nonempty en.name")
    }

    @Test
    fun givenAFormatThreeManifest_whenInstalling_thenTheCatalogIsRejected() = runTest {
        assertRejected(fixtureCatalog(1, format = 3), "Unsupported catalog format 3")
    }

    @Test
    fun givenAReadFailureAfterAComponentBatch_whenInstalling_thenThePreviousCatalogAndIconsSurvive() = runTest {
        val old = fixtureCatalog(1)
        installer.install(old)
        val prefix = List(650) { index ->
            FIXTURE_COMPONENT.replace(FIXTURE_COMPONENT_NAME, "com.example.New${index.toString().padStart(3, '0')}")
        }.joinToString(separator = "\n", postfix = "\n")
        val last = FIXTURE_COMPONENT.replace(FIXTURE_COMPONENT_NAME, "com.example.ZLast")
        val newer = fixtureCatalog(2, components = "$prefix$last\n", icon = "new icon")
        newer.openOverride = { path, bytes ->
            if (path == CATALOG_COMPONENTS) failAfter(prefix.length, bytes) else ByteArrayInputStream(bytes)
        }

        assertRejected(newer, "Injected read failure")

        assertThat(dao.readState()?.sequence).isEqualTo(1L)
        assertThat(dao.readRules().single().rule.id).isEqualTo(1L)
        assertThat(dao.findComponents(listOf(FIXTURE_COMPONENT_NAME, "com.example.New000")).single().component.name)
            .isEqualTo(FIXTURE_COMPONENT_NAME)
        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("old icon")
        assertThat(icons.directory(2).exists()).isFalse()
    }

    @Test
    fun givenAnOlderCatalog_whenInstallingANewerSequence_thenOldIconsLastUntilTheNextProcess() = runTest {
        installer.install(fixtureCatalog(1))
        val old = repository.iconFile(FIXTURE_ICON)

        val result = installer.install(fixtureCatalog(2, icon = "new icon")) as CatalogInstallResult.Installed

        assertThat(result.state.sequence).isEqualTo(2L)
        assertThat(old?.readText()).isEqualTo("old icon")
        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("new icon")

        val restarted = CatalogInstaller(dao, icons, Dispatchers.IO)
        assertThat(restarted.install(fixtureCatalog(2, icon = "new icon"))).isEqualTo(CatalogInstallResult.SkippedNotNewer)
        assertThat(old?.exists()).isFalse()
        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("new icon")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenAnInstallInProgress_whenStartingASecondInstall_thenItWaitsForTheFirstCommit() = runTest {
        val installer = CatalogInstaller(dao, icons, StandardTestDispatcher(testScheduler))
        val firstOpenedComponents = CompletableDeferred<Unit>()
        val releaseFirst = CountDownLatch(1)
        val newer = fixtureCatalog(2)
        newer.openOverride = { path, bytes ->
            if (path == CATALOG_COMPONENTS) {
                firstOpenedComponents.complete(Unit)
                if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw IOException("Timed out waiting to resume fixture")
            }
            ByteArrayInputStream(bytes)
        }

        val first = async { installer.install(newer) }
        firstOpenedComponents.await()
        val older = fixtureCatalog(1)
        val second = async { installer.install(older) }
        try {
            runCurrent()
            assertThat(older.opens).isEmpty()
        } finally {
            releaseFirst.countDown()
        }

        assertThat(first.await()).isInstanceOf(CatalogInstallResult.Installed::class.java)
        assertThat(second.await()).isEqualTo(CatalogInstallResult.SkippedNotNewer)
        assertThat(older.opens).containsExactly(CATALOG_MANIFEST, 1)
        assertThat(dao.readState()?.sequence).isEqualTo(2L)
    }

    @Test
    fun givenAStagedCatalog_whenCancellingTheImport_thenThePreviousCatalogAndIconsSurvive() = runTest {
        installer.install(fixtureCatalog(1))
        val openedComponents = CompletableDeferred<Unit>()
        val releaseImport = CountDownLatch(1)
        val newer = fixtureCatalog(2, icon = "new icon")
        newer.openOverride = { path, bytes ->
            if (path == CATALOG_COMPONENTS) {
                openedComponents.complete(Unit)
                if (!releaseImport.await(10, TimeUnit.SECONDS)) throw IOException("Timed out waiting to cancel fixture")
            }
            ByteArrayInputStream(bytes)
        }

        val job = launch { installer.install(newer) }
        openedComponents.await()
        try {
            assertThat(icons.directory(2).exists()).isTrue()
            job.cancel()
        } finally {
            releaseImport.countDown()
        }
        job.join()

        assertThat(job.isCancelled).isTrue()
        assertThat(dao.readState()?.sequence).isEqualTo(1L)
        assertThat(repository.iconFile(FIXTURE_ICON)?.readText()).isEqualTo("old icon")
        assertThat(icons.directory(2).exists()).isFalse()
    }

    @Test
    fun givenTheBundledSeed_whenInstalling_thenSequenceOneHasThePublishedCounts() = runTest {
        val start = Monotonic.markNow()

        val result = repository.installBundled() as CatalogInstallResult.Installed

        println("Bundled catalog JVM import took ${start.elapsedNow()}")
        assertThat(result.state.sequence).isEqualTo(1L)
        assertThat(dao.readRules()).hasSize(512)
        val componentCount = withContext(Dispatchers.IO) {
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM catalog_component").use {
                check(it.moveToFirst())
                it.getInt(0)
            }
        }
        assertThat(componentCount).isEqualTo(15_691)
    }

    private suspend fun assertRejected(source: CatalogSource, reason: String) {
        val result = installer.install(source) as CatalogInstallResult.Rejected
        assertThat(result.reason).contains(reason)
    }

    private fun failAfter(limit: Int, bytes: ByteArray): InputStream = object : InputStream() {
        private val source = ByteArrayInputStream(bytes, 0, limit)

        override fun read(): Int = source.read().checked()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source.read(buffer, offset, length).checked()

        private fun Int.checked(): Int {
            if (this < 0) throw IOException("Injected read failure")
            return this
        }
    }
}
