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

package com.merxury.blocker.feature.impl.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.NoOpAnalyticsHelper
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.rule.entity.resultMessage
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestSyncManager
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** The rule work the settings screen starts, and the events it shows for it. */
@RunWith(RobolectricTestRunner::class)
class SettingsRuleWorkTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userDataRepository = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    private val workManager = mock<WorkManager>()

    private val viewModel = SettingsViewModel(
        appContext = application,
        userDataRepository = userDataRepository,
        blockerPreferences = mock<BlockerPreferencesDataSource>(),
        syncManager = TestSyncManager(),
        analyticsHelper = NoOpAnalyticsHelper(),
        workManager = workManager,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )

    private fun ended(state: WorkInfo.State, vararg output: Pair<String, Any>) = MutableLiveData(listOf(WorkInfo(UUID.randomUUID(), state, emptySet(), workDataOf(*output))))

    @Test
    fun givenMatImport_whenItFinishes_thenTheScreenGetsItsResult() = runTest {
        whenever(workManager.getWorkInfosForUniqueWorkLiveData("ImportMatRule")) doReturn
            ended(WorkInfo.State.SUCCEEDED, RuleWorkResult.PARAM_CHANGED_COUNT to 3, RuleWorkResult.PARAM_SKIPPED_COUNT to 1)
        val events = mutableListOf<RuleWorkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.importMyAndroidToolsRules(Uri.parse("content://docs/mat.txt"))

        verify(workManager).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
        assertThat(events).containsExactly(RuleWorkEvent(RuleWorkType.IMPORT_MAT_RULES, RuleWorkResult.FINISHED, changed = 3, skipped = 1))
        assertThat(events.single().resultMessage(application.resources)).isEqualTo("Done. 3 items changed. 1 item skipped.")
    }

    @Test
    fun givenImportWithAnUnreadableFile_whenItFinishes_thenTheMessageCountsTheFailureInsteadOfAnError() = runTest {
        whenever(workManager.getWorkInfosForUniqueWorkLiveData("ImportBlockerRule")) doReturn ended(
            WorkInfo.State.SUCCEEDED,
            RuleWorkResult.PARAM_CHANGED_COUNT to 0,
            RuleWorkResult.PARAM_UNCHANGED_COUNT to 145,
            RuleWorkResult.PARAM_FAILED_COUNT to 1,
        )
        val events = mutableListOf<RuleWorkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.importBlockerRules()

        assertThat(events.single().resultMessage(application.resources)).isEqualTo("Done. 0 items changed. 1 item failed.")
    }

    @Test
    fun givenExportWithoutTheFirewall_whenItFinishes_thenTheMessageSaysTheRulesWereNotIncluded() = runTest {
        whenever(workManager.getWorkInfosForUniqueWorkLiveData("ExportBlockerRule")) doReturn
            ended(WorkInfo.State.SUCCEEDED, RuleWorkResult.PARAM_CHANGED_COUNT to 7, RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE to true)
        val events = mutableListOf<RuleWorkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.exportBlockerRules()

        assertThat(events.single().resultMessage(application.resources))
            .isEqualTo("Done. 7 items changed. The Intent Firewall rules could not be read and were not included.")
    }

    @Test
    fun givenUnreachableFolder_whenImportFails_thenTheMessageSaysTheFolderCannotBeAccessed() = runTest {
        whenever(workManager.getWorkInfosForUniqueWorkLiveData("ImportIfwRule")) doReturn
            ended(WorkInfo.State.FAILED, RuleWorkResult.PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION)
        val events = mutableListOf<RuleWorkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }

        viewModel.importIfwRules()

        assertThat(events.single().resultMessage(application.resources)).isEqualTo("Cannot access the backup folder. Please choose it again in settings.")
    }
}
