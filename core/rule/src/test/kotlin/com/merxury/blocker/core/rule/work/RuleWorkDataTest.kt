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

package com.merxury.blocker.core.rule.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.backup.FailureReason
import com.merxury.blocker.core.rule.backup.PackageManagerReset
import com.merxury.blocker.core.rule.backup.RuleRunResult
import com.merxury.blocker.core.rule.backup.RuleRunSummary
import com.merxury.blocker.core.rule.backup.SkipReason
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.testing.controller.FakeIntentFirewall
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuleWorkDataTest {

    private fun ListenableWorker.Result.output(): Data = when (this) {
        is ListenableWorker.Result.Success -> outputData
        is ListenableWorker.Result.Failure -> outputData
        else -> error("Unexpected $this")
    }

    private fun completed(summary: RuleRunSummary) = RuleRunResult.Completed(summary).toWorkResult("param_import_count") { it.apps }

    @Test
    fun givenRunThatCouldNotStart_whenMapping_thenEachReasonGetsItsCode() {
        fun code(reason: FailureReason) = (RuleRunResult.Failed(reason).toWorkResult("k") { 0 } as ListenableWorker.Result.Failure)
            .outputData.getInt(RuleWorkResult.PARAM_WORK_RESULT, -1)

        assertThat(code(FailureReason.STORAGE_UNAVAILABLE)).isEqualTo(RuleWorkResult.MISSING_STORAGE_PERMISSION)
        assertThat(code(FailureReason.NO_PRIVILEGE)).isEqualTo(RuleWorkResult.MISSING_ROOT_PERMISSION)
        assertThat(code(FailureReason.STATE_UNREADABLE)).isEqualTo(RuleWorkResult.UNEXPECTED_EXCEPTION)
        assertThat(code(FailureReason.SYSTEM_ERROR)).isEqualTo(RuleWorkResult.UNEXPECTED_EXCEPTION)
    }

    @Test
    fun givenRunThatAppliedSomeAndFailedOthers_whenMapping_thenItSucceedsWithTheCounts() {
        val result = completed(RuleRunSummary(apps = 2, applied = 5, unchanged = 1, skipped = mapOf(SkipReason.NOT_DECLARED to 3), failed = mapOf(FailureReason.NO_PRIVILEGE to 4), firewallUnavailable = true))

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(result.output().keyValueMap).containsExactly(
            "param_import_count",
            2,
            RuleWorkResult.PARAM_FAILED_COUNT,
            4,
            RuleWorkResult.PARAM_SKIPPED_COUNT,
            3,
            RuleWorkResult.PARAM_UNCHANGED_COUNT,
            1,
            RuleWorkResult.PARAM_UNREADABLE_APP_COUNT,
            0,
            RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE,
            true,
        )
    }

    @Test
    fun givenNothingAppliedAndOnlyPrivilegeFailures_whenMapping_thenMissingRootPermission() {
        val result = completed(RuleRunSummary(failed = mapOf(FailureReason.NO_PRIVILEGE to 2)))

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(result.output().getInt(RuleWorkResult.PARAM_WORK_RESULT, -1)).isEqualTo(RuleWorkResult.MISSING_ROOT_PERMISSION)
        assertThat(result.output().getInt(RuleWorkResult.PARAM_FAILED_COUNT, -1)).isEqualTo(2)
    }

    @Test
    fun givenNothingAppliedAndOtherFailures_whenMapping_thenUnexpectedException() {
        val result = completed(RuleRunSummary(failed = mapOf(FailureReason.NO_PRIVILEGE to 1, FailureReason.BACKUP_UNREADABLE to 1)))

        assertThat(result.output().getInt(RuleWorkResult.PARAM_WORK_RESULT, -1)).isEqualTo(RuleWorkResult.UNEXPECTED_EXCEPTION)
    }

    @Test
    fun givenNothingToDo_whenMapping_thenItSucceeds() {
        assertThat(completed(RuleRunSummary())).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }

    @Test
    fun givenNoSourceSet_whenReadingImportInput_thenTheTargetUsersOwnSetIsRead() {
        assertThat(Data.EMPTY.sourceSetFor(UserId.SYSTEM)).isEqualTo(BackupSet.OWNER)
        assertThat(Data.EMPTY.sourceSetFor(UserId(10))).isEqualTo(BackupSet(UserId(10)))
        assertThat(workDataOf(PARAM_SOURCE_SET_USER to 0).sourceSetFor(UserId(10))).isEqualTo(BackupSet.OWNER)
    }

    @Test
    fun givenNoOrInvalidUser_whenReadingInput_thenTheDefaultIsUsed() {
        val default = UserId(5)

        assertThat(Data.EMPTY.userOr(PARAM_TARGET_USER, default)).isEqualTo(default)
        assertThat(workDataOf(PARAM_TARGET_USER to -2).userOr(PARAM_TARGET_USER, default)).isEqualTo(default)
        assertThat(workDataOf(PARAM_TARGET_USER to 10).userOr(PARAM_TARGET_USER, default)).isEqualTo(UserId(10))
    }

    @Test
    fun givenNoUserInput_whenResetPmWorkRuns_thenItResetsTheUserBlockerRunsIn() = runTest {
        val components = FakeComponentStatusRepository()
        val boot = ComponentDeclaration(ComponentId("com.example", "com.example.Boot"), ComponentType.RECEIVER, exported = true, enabledInManifest = true)
        components.sendComponents(ownUser(), listOf(ComponentStatus(boot, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))))
        components.sendComponents(UserId(10), listOf(ComponentStatus(boot, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))))
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The notification's cancel action asks WorkManager for an intent.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val worker = TestListenableWorkerBuilder<ResetPmWorker>(context, inputData = ResetPmWorker.resetPmWork().workSpec.input)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) = ResetPmWorker(
                        appContext,
                        workerParameters,
                        PackageManagerReset(components, FakePrivilegedChannel()),
                        LegacyComponentCache(TestComponentRepository(), FakeIntentFirewall()),
                    )
                },
            )
            .build()

        val result = worker.doWork()

        assertThat(components.requests.map { it.user }).containsExactly(ownUser())
        assertThat(result.output().getInt(ResetPmWorker.PARAM_RESTORED_COUNT, -1)).isEqualTo(1)
    }
}
