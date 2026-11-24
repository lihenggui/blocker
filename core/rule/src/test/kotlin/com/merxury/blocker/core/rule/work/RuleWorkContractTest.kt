// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import android.net.Uri
import androidx.work.WorkRequest
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the result codes and the WorkManager input and output keys that the settings and app
 * detail screens read, so a rewrite of the workers cannot silently change them.
 */
@RunWith(RobolectricTestRunner::class)
class RuleWorkContractTest {

    private fun inputOf(request: WorkRequest): Map<String, Any?> = request.workSpec.input.keyValueMap

    @Test
    fun whenReadingRuleWorkResult_thenCodesAndKeyAreUnchanged() {
        assertThat(RuleWorkResult.PARAM_WORK_RESULT).isEqualTo("param_work_result")
        assertThat(RuleWorkResult.STARTED).isEqualTo(0)
        assertThat(RuleWorkResult.FINISHED).isEqualTo(1)
        assertThat(RuleWorkResult.FOLDER_NOT_DEFINED).isEqualTo(2)
        assertThat(RuleWorkResult.MISSING_ROOT_PERMISSION).isEqualTo(3)
        assertThat(RuleWorkResult.MISSING_STORAGE_PERMISSION).isEqualTo(4)
        assertThat(RuleWorkResult.UNEXPECTED_EXCEPTION).isEqualTo(5)
        assertThat(RuleWorkResult.CANCELLED).isEqualTo(6)
        assertThat(RuleWorkResult.LEGACY_PM_RECORDS_UNREADABLE).isEqualTo(7)
    }

    @Test
    fun whenReadingWorkerOutputKeys_thenKeyStringsAreUnchanged() {
        assertThat(ExportBlockerRulesWorker.PARAM_BACKUP_COUNT).isEqualTo("param_backup_count")
        assertThat(ImportBlockerRuleWorker.PARAM_IMPORT_COUNT).isEqualTo("param_import_count")
        assertThat(ExportIfwRulesWorker.PARAM_EXPORT_COUNT).isEqualTo("param_export_count")
        assertThat(ImportIfwRulesWorker.PARAM_IMPORT_COUNT).isEqualTo("param_import_count")
        assertThat(ImportMatRulesWorker.PARAM_IMPORT_COUNT).isEqualTo("param_import_count")
        assertThat(ResetIfwWorker.PARAM_CLEAR_COUNT).isEqualTo("param_clear_count")
        assertThat(ResetPmWorker.PARAM_RESTORED_COUNT).isEqualTo("param_restored_count")
    }

    @Test
    fun whenReadingAddedOutputKeys_thenKeyStringsAreAsListed() {
        assertThat(RuleWorkResult.PARAM_FAILED_COUNT).isEqualTo("param_failed_count")
        assertThat(RuleWorkResult.PARAM_SKIPPED_COUNT).isEqualTo("param_skipped_count")
        assertThat(RuleWorkResult.PARAM_UNCHANGED_COUNT).isEqualTo("param_unchanged_count")
        assertThat(RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE).isEqualTo("param_firewall_unavailable")
        assertThat(RuleWorkResult.PARAM_UNREADABLE_APP_COUNT).isEqualTo("param_unreadable_app_count")
        assertThat(RuleWorkResult.PARAM_CHANGED_COUNT).isEqualTo("param_changed_count")
    }

    @Test
    fun givenBlockerRuleExport_whenBuildingWork_thenInputKeysAreUnchanged() {
        val request = ExportBlockerRulesWorker.exportWork(
            folderPath = "content://tree/backup",
            backupSystemApps = true,
            backupPackageName = "com.example.app",
        )

        assertThat(inputOf(request)).containsExactly(
            "param_folder_path",
            "content://tree/backup",
            "param_backup_system_apps",
            true,
            "param_backup_app_package_name",
            "com.example.app",
        )
    }

    @Test
    fun givenBlockerRuleImport_whenBuildingWork_thenInputKeysAreUnchanged() {
        val request = ImportBlockerRuleWorker.importWork(
            backupPath = "content://tree/backup",
            restoreSystemApps = true,
            backupPackageName = "com.example.app",
        )

        // The controller type no longer travels: the file's methods pick the layers, and an
        // ordinal queued by an older build is ignored.
        assertThat(inputOf(request)).containsExactly(
            "param_folder_path",
            "content://tree/backup",
            "param_restore_sys_apps",
            true,
            "param_backup_package_name",
            "com.example.app",
        )
    }

    @Test
    fun givenTargetUserAndSourceSet_whenBuildingImportWork_thenTheirIdsAreAdded() {
        val request = ImportBlockerRuleWorker.importWork(
            backupPath = "content://tree/backup",
            restoreSystemApps = false,
            targetUser = UserId(10),
            sourceSet = BackupSet(UserId(11)),
        )

        assertThat(inputOf(request)).containsExactly(
            "param_folder_path",
            "content://tree/backup",
            "param_restore_sys_apps",
            false,
            "param_target_user",
            10,
            "param_source_set_user",
            11,
        )
    }

    @Test
    fun givenTargetUser_whenBuildingExportAndResetWork_thenItsIdIsAdded() {
        val export = ExportBlockerRulesWorker.exportWork("content://tree/backup", backupSystemApps = false, user = UserId(10))

        assertThat(inputOf(export)).containsEntry("param_target_user", 10)
        assertThat(inputOf(ResetPmWorker.resetPmWork(UserId(10)))).containsExactly("param_target_user", 10)
        assertThat(inputOf(ResetPmWorker.resetPmWork())).isEmpty()
    }

    @Test
    fun givenIfwExport_whenBuildingWork_thenInputKeysAreUnchanged() {
        val request = ExportIfwRulesWorker.exportWork("content://tree/backup", "com.example.app")

        assertThat(inputOf(request)).containsExactly(
            "param_folder_path",
            "content://tree/backup",
            "param_backup_package_name",
            "com.example.app",
        )
    }

    @Test
    fun givenIfwImport_whenBuildingWork_thenInputKeysAreUnchanged() {
        val request = ImportIfwRulesWorker.importIfwWork("content://tree/backup", true, "com.example.app")

        assertThat(inputOf(request)).containsExactly(
            "param_folder_path",
            "content://tree/backup",
            "param_restore_sys_apps",
            true,
            "param_restore_package_name",
            "com.example.app",
        )
    }

    @Test
    fun givenMatImport_whenBuildingWork_thenInputKeysAreUnchangedWithoutTheControllerType() {
        val request = ImportMatRulesWorker.importWork(
            fileUri = Uri.parse("content://docs/mat.txt"),
            restoreSystemApps = false,
        )

        // The controller type is read when the work runs instead of travelling as an ordinal.
        assertThat(inputOf(request)).containsExactly(
            "key_file_uri",
            "content://docs/mat.txt",
            "param_restore_sys_apps",
            false,
        )
    }

    @Test
    fun givenResetWorks_whenBuildingWork_thenInputKeysAreUnchanged() {
        assertThat(inputOf(ResetIfwWorker.clearIfwWork("com.example.app"))).containsExactly(
            "param_reset_package_name",
            "com.example.app",
        )
    }
}
