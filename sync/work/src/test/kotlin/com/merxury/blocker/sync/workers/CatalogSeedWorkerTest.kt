// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.repository.catalog.CatalogInstallResult
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.model.CatalogState
import com.merxury.blocker.core.testing.repository.TestCatalogRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogSeedWorkerTest {
    @get:Rule val filesDir = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = InMemoryDataStore(UserPreferences.getDefaultInstance())
    private var installResult: CatalogInstallResult = CatalogInstallResult.SkippedNotNewer
    private var installs = 0
    private val catalog = object : CatalogRepository by TestCatalogRepository() {
        override suspend fun installBundled(): CatalogInstallResult {
            installs++
            return installResult
        }
    }

    @Test
    fun givenBundledCatalogIsInstalled_whenCleanupSucceeds_thenWorkSucceeds() = runTest {
        installResult = CatalogInstallResult.Installed(CatalogState(2, 2, "fixture", "2026-01-01T00:00:00Z"))
        val checkout = filesDir.newFolder("blocker-general-rules")

        assertThat(worker().doWork()).isEqualTo(Result.success())
        assertThat(installs).isEqualTo(1)
        assertThat(checkout.exists()).isFalse()
    }

    @Test
    fun givenBundledCatalogIsNotNewer_whenCleanupSucceeds_thenWorkSucceeds() = runTest {
        val checkout = filesDir.newFolder("blocker-general-rules")

        assertThat(worker().doWork()).isEqualTo(Result.success())
        assertThat(installs).isEqualTo(1)
        assertThat(checkout.exists()).isFalse()
    }

    @Test
    fun givenBundledCatalogIsRejected_whenCleanupSucceeds_thenWorkFails() = runTest {
        installResult = CatalogInstallResult.Rejected("Invalid bundled catalog")
        val checkout = filesDir.newFolder("blocker-general-rules")

        assertThat(worker().doWork()).isEqualTo(Result.failure())
        assertThat(installs).isEqualTo(1)
        assertThat(checkout.exists()).isFalse()
    }

    @Test
    fun givenCleanupFails_whenAnAttemptRemains_thenWorkRetries() = runTest {
        assertThat(worker(attempt = MAX_RETRY_ATTEMPTS - 2, dataStore = unwritableStore()).doWork())
            .isEqualTo(Result.retry())
        assertThat(installs).isEqualTo(1)
    }

    @Test
    fun givenCleanupFails_whenTheFinalAttemptRuns_thenWorkFails() = runTest {
        assertThat(worker(attempt = MAX_RETRY_ATTEMPTS - 1, dataStore = unwritableStore()).doWork())
            .isEqualTo(Result.failure())
        assertThat(installs).isEqualTo(1)
    }

    @Test
    fun givenTheRetryLimitIsReached_whenWorkRuns_thenItFailsWithoutInstallingOrCleaningUp() = runTest {
        val checkout = filesDir.newFolder("blocker-general-rules")

        assertThat(worker(attempt = MAX_RETRY_ATTEMPTS).doWork()).isEqualTo(Result.failure())
        assertThat(installs).isEqualTo(0)
        assertThat(checkout.isDirectory).isTrue()
    }

    private fun unwritableStore(): DataStore<UserPreferences> = object : DataStore<UserPreferences> by store {
        override suspend fun updateData(transform: suspend (UserPreferences) -> UserPreferences): UserPreferences = throw IOException("Preferences are unwritable")
    }

    private fun TestScope.worker(attempt: Int = 0, dataStore: DataStore<UserPreferences> = store): CatalogSeedWorker {
        val cleanup = LegacyRuleCacheCleanup(filesDir.root, catalog, BlockerPreferencesDataSource(dataStore), StandardTestDispatcher(testScheduler))
        return TestListenableWorkerBuilder<CatalogSeedWorker>(context)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) = CatalogSeedWorker(appContext, workerParameters, catalog, cleanup)
                },
            )
            .build()
    }
}
