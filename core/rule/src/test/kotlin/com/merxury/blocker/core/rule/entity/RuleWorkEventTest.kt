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

package com.merxury.blocker.core.rule.entity

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RuleWorkEventTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun finished(changed: Int, skipped: Int = 0, failed: Int = 0, firewallUnavailable: Boolean = false) = RuleWorkEvent(RuleWorkType.IMPORT_BLOCKER_RULES, RuleWorkResult.FINISHED, changed, skipped, failed, firewallUnavailable)

    @Test
    fun givenSucceededWork_whenMakingTheEvent_thenItCarriesTheCounts() {
        val info = WorkInfo(
            UUID.randomUUID(),
            WorkInfo.State.SUCCEEDED,
            emptySet(),
            workDataOf(
                RuleWorkResult.PARAM_CHANGED_COUNT to 4,
                RuleWorkResult.PARAM_SKIPPED_COUNT to 2,
                RuleWorkResult.PARAM_FAILED_COUNT to 1,
                RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE to true,
            ),
        )

        assertThat(ruleWorkEventOf(RuleWorkType.EXPORT_BLOCKER_RULES, info)).isEqualTo(
            RuleWorkEvent(RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkResult.FINISHED, changed = 4, skipped = 2, failed = 1, firewallUnavailable = true),
        )
    }

    @Test
    fun givenFinishedWork_whenMakingTheMessage_thenItSaysWhatChangedAndOnlyNonZeroOtherCounts() {
        assertThat(finished(changed = 4).resultMessage(resources)).isEqualTo("Done. 4 items changed.")
        assertThat(finished(changed = 1, skipped = 2).resultMessage(resources)).isEqualTo("Done. 1 item changed. 2 items skipped.")
        assertThat(finished(changed = 0, skipped = 0, failed = 1).resultMessage(resources)).isEqualTo("Done. 0 items changed. 1 item failed.")
    }

    @Test
    fun givenExportWithoutTheFirewall_whenMakingTheMessage_thenItSaysTheRulesWereLeftOut() {
        assertThat(finished(changed = 3, firewallUnavailable = true).resultMessage(resources))
            .isEqualTo("Done. 3 items changed. Intent Firewall rules could not be read, so they were left out.")
    }

    @Test
    fun givenFolderUnsetOrUnreachable_whenMakingTheMessage_thenTheyDiffer() {
        val unset = RuleWorkEvent(RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkResult.FOLDER_NOT_DEFINED).resultMessage(resources)
        val unreachable = RuleWorkEvent(RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkResult.MISSING_STORAGE_PERMISSION).resultMessage(resources)

        assertThat(unset).isEqualTo("Backup folder is not defined, please go to settings to assign one.")
        assertThat(unreachable).isEqualTo("Cannot access the backup folder. Please choose it again in settings.")
    }

    @Test
    fun givenStorageFailures_whenMakingTheMessage_thenOnlyTheBackupWorksMentionTheFolder() {
        fun message(type: RuleWorkType, result: Int) = RuleWorkEvent(type, result).resultMessage(resources)

        assertThat(message(RuleWorkType.IMPORT_MAT_RULES, RuleWorkResult.MISSING_STORAGE_PERMISSION))
            .isEqualTo("Cannot read the selected file. Please choose it again.")
        for (type in listOf(RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkType.IMPORT_BLOCKER_RULES, RuleWorkType.EXPORT_IFW_RULES, RuleWorkType.IMPORT_IFW_RULES)) {
            assertThat(message(type, RuleWorkResult.MISSING_STORAGE_PERMISSION)).isEqualTo("Cannot access the backup folder. Please choose it again in settings.")
        }
        for (type in listOf(RuleWorkType.RESET_IFW, RuleWorkType.RESET_PM)) {
            assertThat(message(type, RuleWorkResult.MISSING_STORAGE_PERMISSION)).doesNotContain("folder")
            assertThat(message(type, RuleWorkResult.FOLDER_NOT_DEFINED)).doesNotContain("folder")
        }
    }

    @Test
    fun givenStartedWork_whenMakingTheMessage_thenTheScreenChoosesIt() {
        assertThat(RuleWorkEvent(RuleWorkType.RESET_PM, RuleWorkResult.STARTED).resultMessage(resources)).isNull()
    }
}
