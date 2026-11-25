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
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult.Reason
import com.merxury.blocker.core.model.CatalogState
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.core.testing.repository.TestCatalogRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.utils.AppDebugChecker
import com.merxury.blocker.sync.status.ISyncSubscriber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LAST_SYNCED_TIME = "last_synced_time"
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("sync_rule", Context.MODE_PRIVATE)
    private val userData = TestUserDataRepository()
    private val catalog = TestCatalogRepository()
    private val analytics = TestAnalyticsHelper()
    private var subscriptions = 0
    private val staleTime = System.currentTimeMillis() - DAY_MILLIS - 1

    @Before
    fun setUp() {
        userData.sendUserData(defaultUserData)
        preferences.edit { putLong(LAST_SYNCED_TIME, staleTime) }
    }

    @Test
    fun givenANewerCatalog_whenSyncing_thenItSucceedsAndRecordsTheSyncTime() = runTest {
        catalog.updateResult = CatalogUpdateResult.Updated(CatalogState(2, 2, "fixture", "2026-01-01T00:00:00Z"))

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
        assertThat(lastSyncedTime()).isGreaterThan(staleTime)
        assertThat(subscriptions).isEqualTo(1)
        assertThat(analytics.loggedEvents.map { it.type })
            .containsExactly("network_sync_started", "network_sync_successful").inOrder()
    }

    @Test
    fun givenTheCatalogIsCurrent_whenSyncing_thenItSucceedsAndRecordsTheSyncTime() = runTest {
        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).containsExactly(GITHUB)
        assertThat(lastSyncedTime()).isGreaterThan(staleTime)
    }

    @Test
    fun givenANetworkFailure_whenSyncing_thenItRetriesWithoutRecordingSuccess() = runTest {
        catalog.updateResult = CatalogUpdateResult.Failed(Reason.NETWORK, "Connection dropped")

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.retry())
        assertThat(lastSyncedTime()).isEqualTo(staleTime)
        assertThat(analytics.loggedEvents.map { it.type })
            .containsExactly("network_sync_started", "network_sync_failed").inOrder()
    }

    @Test
    fun givenAnInvalidRelease_whenSyncing_thenItFailsWithoutRecordingSuccess() = runTest {
        catalog.updateResult = CatalogUpdateResult.Failed(Reason.INVALID_DATA, "Invalid release")

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.failure())
        assertThat(lastSyncedTime()).isEqualTo(staleTime)
    }

    @Test
    fun givenASyncWithinTheDay_whenStartingNormally_thenNoCheckIsMade() = runTest {
        val recent = System.currentTimeMillis() - DAY_MILLIS + 1_000L
        preferences.edit { putLong(LAST_SYNCED_TIME, recent) }

        assertThat(worker(StandardTestDispatcher(testScheduler)).doWork()).isEqualTo(Result.success())
        assertThat(catalog.updateRequests).isEmpty()
        assertThat(lastSyncedTime()).isEqualTo(recent)
        assertThat(analytics.loggedEvents).isEmpty()
    }

    @Test
    fun givenARecentSync_whenStartingInDebugMode_thenItChecksAgain() = runTest {
        preferences.edit { putLong(LAST_SYNCED_TIME, System.currentTimeMillis()) }

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

    private fun lastSyncedTime() = preferences.getLong(LAST_SYNCED_TIME, 0)

    private fun worker(dispatcher: CoroutineDispatcher, debug: Boolean = false, forceCheck: Boolean = false): SyncWorker {
        val work = SyncWorker.startUpSyncWork(forceCheck)
        val params = mock<WorkerParameters> { on { inputData } doReturn work.workSpec.input }
        return SyncWorker(
            appContext = context,
            workerParams = params,
            userDataRepository = userData,
            catalogRepository = catalog,
            ioDispatcher = dispatcher,
            analyticsHelper = analytics,
            syncSubscriber = object : ISyncSubscriber {
                override suspend fun subscribe() {
                    subscriptions++
                }
            },
            appDebugChecker = object : AppDebugChecker {
                override fun isDebugMode() = debug
            },
        )
    }
}
