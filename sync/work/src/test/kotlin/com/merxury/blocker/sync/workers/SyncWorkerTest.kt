/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.sync.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult.Reason
import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.datastore.appProperties
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.model.CatalogState
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.core.testing.repository.TestCatalogRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.utils.AppDebugChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userData = TestUserDataRepository()
    private val catalog = TestCatalogRepository()
    private val analytics = TestAnalyticsHelper()
    private val now = Instant.parse("2026-11-27T12:00:00Z")
    private val staleTime = now.toEpochMilliseconds() - DAY_MILLIS - 1
    private val store = InMemoryDataStore<AppProperties>(appProperties { lastCatalogSyncedEpochMs = staleTime })
    private val propertiesDataStore = BlockerAppPropertiesDataStore(store)

    @Before
    fun setUp() {
        userData.sendUserData(defaultUserData)
    }

    @Test
    fun givenANewerCatalog_whenSyncing_thenItSucceedsAndRecordsTheSyncTime() = runTest {
        catalog.updateResult = CatalogUpdateResult.Updated(CatalogState(2, 2, "fixture", "2026-01-01T00:00:00Z"))

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
        assertThat(lastSyncedTime()).isEqualTo(now.toEpochMilliseconds())
        assertThat(analytics.loggedEvents.map { it.type })
            .containsExactly("network_sync_started", "network_sync_successful")
            .inOrder()
    }

    @Test
    fun givenTheCatalogIsCurrent_whenSyncing_thenItSucceedsAndRecordsTheSyncTime() = runTest {
        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
        assertThat(lastSyncedTime()).isEqualTo(now.toEpochMilliseconds())
    }

    @Test
    fun givenANetworkFailure_whenSyncing_thenItRetriesWithoutRecordingSuccess() = runTest {
        catalog.updateResult = CatalogUpdateResult.Failed(Reason.NETWORK, "Connection dropped")

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.retry())
        assertThat(lastSyncedTime()).isEqualTo(staleTime)
        assertThat(analytics.loggedEvents.map { it.type })
            .containsExactly("network_sync_started", "network_sync_failed")
            .inOrder()
    }

    @Test
    fun givenAnInvalidRelease_whenSyncing_thenItFailsWithoutRecordingSuccess() = runTest {
        catalog.updateResult = CatalogUpdateResult.Failed(Reason.INVALID_DATA, "Invalid release")

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.failure())
        assertThat(lastSyncedTime()).isEqualTo(staleTime)
    }

    @Test
    fun givenASyncWithinTheDay_whenStartingNormally_thenNoCheckIsMade() = runTest {
        val recent = now.toEpochMilliseconds() - DAY_MILLIS + 1_000L
        propertiesDataStore.setLastCatalogSyncedEpochMs(recent)

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).isEmpty()
        assertThat(lastSyncedTime()).isEqualTo(recent)
        assertThat(analytics.loggedEvents).isEmpty()
    }

    @Test
    fun givenARecentSync_whenStartingInDebugMode_thenItChecksAgain() = runTest {
        propertiesDataStore.setLastCatalogSyncedEpochMs(now.toEpochMilliseconds())

        assertThat(worker(StandardTestDispatcher(testScheduler), debug = true).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
    }

    @Test
    fun givenGitHubWasJustChecked_whenSwitchingProviders_thenTheImmediateWorkChecksGitLab() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        assertThat(worker(dispatcher).doWork()).isEqualTo(Result.success())
        userData.setRuleServerProvider(GITLAB)

        assertThat(worker(dispatcher, forceCheck = true).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB, GITLAB).inOrder()
    }

    @Test
    fun givenClockRollback_whenStartingNormally_thenSyncReplacesTheFutureTimestamp() = runTest {
        propertiesDataStore.setLastCatalogSyncedEpochMs(now.toEpochMilliseconds() + DAY_MILLIS)

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
        assertThat(lastSyncedTime()).isEqualTo(now.toEpochMilliseconds())
    }

    @Test
    fun givenRetryLimit_whenStartingAgain_thenNoCatalogRequestIsMade() = runTest {
        catalog.updateResult = CatalogUpdateResult.Failed(Reason.NETWORK, "Connection dropped")
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertThat(worker(dispatcher, attempt = MAX_RETRY_ATTEMPTS - 1).doWork()).isEqualTo(Result.retry())
        assertThat(worker(dispatcher, attempt = MAX_RETRY_ATTEMPTS).doWork()).isEqualTo(Result.failure())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
        assertThat(lastSyncedTime()).isEqualTo(staleTime)
    }

    @Test
    fun whenGettingForegroundInfo_thenNotificationIdIsNonZero() = runTest {
        val info = worker(StandardTestDispatcher(testScheduler)).getForegroundInfo()

        assertThat(info.notificationId).isEqualTo(1001)
    }

    private fun lastSyncedTime() = store.data.value.lastCatalogSyncedEpochMs

    private fun worker(
        dispatcher: CoroutineDispatcher,
        debug: Boolean = false,
        forceCheck: Boolean = false,
        attempt: Int = 0,
    ): SyncWorker {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SyncWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    userDataRepository = userData,
                    catalogRepository = catalog,
                    ioDispatcher = dispatcher,
                    analyticsHelper = analytics,
                    appProperties = propertiesDataStore,
                    clock =
                    object : Clock {
                        override fun now() = this@SyncWorkerTest.now
                    },
                    appDebugChecker =
                    object : AppDebugChecker {
                        override fun isDebugMode() = debug
                    },
                )
            }
        return TestListenableWorkerBuilder
            .from(context, SyncWorker::class.java)
            .setInputData(SyncWorker.startUpSyncWork(forceCheck).workSpec.input)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
    }
}
