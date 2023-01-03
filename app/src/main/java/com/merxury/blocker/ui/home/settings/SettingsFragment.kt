/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.ui.home.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.elvishew.xlog.LogUtils
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.network.model.OnlineSourceType
import com.merxury.blocker.core.rule.work.ExportBlockerRulesWorker
import com.merxury.blocker.core.rule.work.ExportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportBlockerRuleWorker
import com.merxury.blocker.core.rule.work.ImportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportMatRulesWorker
import com.merxury.blocker.core.rule.work.ResetIfwWorker
import com.merxury.blocker.util.BrowserUtil
import com.merxury.blocker.util.ShareUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.work.CheckRuleUpdateWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment :
    PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener {
    private val logger = XLog.tag("PreferenceFragment")
    private lateinit var sp: SharedPreferences

    private var controllerTypePreference: Preference? = null
    private var onlineRuleSourcePreference: Preference? = null
    private var exportRulePreference: Preference? = null
    private var importRulePreference: Preference? = null
    private var exportIfwRulePreference: Preference? = null
    private var importIfwRulePreference: Preference? = null
    private var resetIfwPreference: Preference? = null
    private var importMatRulesPreference: Preference? = null
    private var storagePreference: Preference? = null
    private var backupSystemAppPreference: SwitchPreference? = null
    private var restoreSystemAppPreference: SwitchPreference? = null
    private var aboutPreference: Preference? = null
    private var repoPreference: Preference? = null
    private var groupPreference: Preference? = null
    private var reportPreference: Preference? = null
    private var updateRulePreference: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        findPreference()
        initPreference()
        initListener()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        logger.d("Preference: ${preference.key} changed, value = $newValue")
        if (preference == onlineRuleSourcePreference) {
            val type = if (newValue == "github") {
                OnlineSourceType.GITHUB
            } else {
                OnlineSourceType.GITLAB
            }
            logger.i("Set online rule source to $type")
            setOnlineSourceType(requireContext(), type)
        }
        return true
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        logger.d("Preference: ${preference.key} clicked")
        when (preference) {
            storagePreference -> selectFolder()
            exportRulePreference -> exportBlockerRule()
            importRulePreference -> importBlockerRule()
            exportIfwRulePreference -> exportIfwRule()
            importIfwRulePreference -> importIfwRule()
            resetIfwPreference -> resetIfw()
            importMatRulesPreference -> selectMatFile()
            aboutPreference -> showAbout()
            groupPreference -> showDiscussionGroup()
            reportPreference -> reportIssue()
            repoPreference -> showRulesRepo()
            updateRulePreference -> updateOnlineRule()
        }
        return true
    }

    private val getFolderResult =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) {
                logger.w("Get folder failed")
                return@registerForActivityResult
            }
            logger.d("Save folder: $uri")
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireActivity().contentResolver.takePersistableUriPermission(uri, flags)
            PreferenceUtil.setRulePath(requireContext(), uri)
            updateFolderSummary()
        }

    private val importMatResult =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                logger.w("Get MAT file failed")
                return@registerForActivityResult
            }
            importMatRule(uri)
        }

    private fun updateOnlineRule() {
        val work = OneTimeWorkRequestBuilder<CheckRuleUpdateWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("UpdateRule", ExistingWorkPolicy.KEEP, work)
    }

    private fun importBlockerRule() {
        val backupPath = PreferenceUtil.getSavedRulePath(requireContext())
        val restoreSystemApps = PreferenceUtil.shouldRestoreSystemApps(requireContext())
        val controllerType = PreferenceUtil.getControllerType(requireContext())
        WorkManager.getInstance(requireContext()).apply {
            enqueueUniqueWork(
                "ImportBlockerRule",
                ExistingWorkPolicy.KEEP,
                ImportBlockerRuleWorker.importWork(
                    backupPath = backupPath?.toString(),
                    restoreSystemApps = restoreSystemApps,
                    controllerType = controllerType,
                )
            )
        }
        ToastUtil.showToast(R.string.import_app_rules_please_wait, Toast.LENGTH_LONG)
    }

    private fun exportBlockerRule() {
        val savedFolder = PreferenceUtil.getSavedRulePath(requireContext())
        val backupSystemApps = PreferenceUtil.shouldBackupSystemApps(requireContext())
        WorkManager.getInstance(requireContext()).apply {
            enqueueUniqueWork(
                "ExportBlockerRule",
                ExistingWorkPolicy.KEEP,
                ExportBlockerRulesWorker.exportWork(
                    folderPath = savedFolder?.toString().orEmpty(),
                    backupSystemApps = backupSystemApps
                )
            )
        }
        ToastUtil.showToast(R.string.backing_up_apps_please_wait, Toast.LENGTH_LONG)
    }

    private fun exportIfwRule() {
        val savedFolder = PreferenceUtil.getSavedRulePath(requireContext())
        ToastUtil.showToast(R.string.backing_up_ifw_please_wait, Toast.LENGTH_LONG)
        WorkManager.getInstance(requireContext()).apply {
            enqueueUniqueWork(
                "ExportIfwRule",
                ExistingWorkPolicy.KEEP,
                ExportIfwRulesWorker.exportWork(savedFolder?.toString().orEmpty())
            )
        }
    }

    private fun importIfwRule() {
        ToastUtil.showToast(R.string.import_ifw_please_wait, Toast.LENGTH_LONG)
        val backupPath = PreferenceUtil.getSavedRulePath(requireContext())
        val restoreSystemApps = PreferenceUtil.shouldRestoreSystemApps(requireContext())
        WorkManager.getInstance(requireContext()).apply {
            enqueueUniqueWork(
                "ImportIfwRule",
                ExistingWorkPolicy.KEEP,
                ImportIfwRulesWorker.importIfwWork(
                    backupPath = backupPath.toString(),
                    restoreSystemApps = restoreSystemApps,
                )
            )
        }
    }

    private fun resetIfw() {
        ToastUtil.showToast(R.string.reset_ifw_please_wait, Toast.LENGTH_LONG)
        val exportWork = ResetIfwWorker.clearIfwWork()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ResetIfw", ExistingWorkPolicy.KEEP, exportWork)
    }

    private fun importMatRule(fileUri: Uri) {
        ToastUtil.showToast(R.string.import_mat_rule_please_wait, Toast.LENGTH_LONG)
        WorkManager.getInstance(requireContext()).apply {
            enqueueUniqueWork(
                "ImportMatRule",
                ExistingWorkPolicy.KEEP,
                ImportMatRulesWorker.importWork(fileUri)
            )
        }
    }

    private fun findPreference() {
        controllerTypePreference = findPreference(getString(R.string.key_pref_controller_type))
        onlineRuleSourcePreference = findPreference(getString(R.string.key_pref_online_rule_source))
        exportRulePreference = findPreference(getString(R.string.key_pref_export_rules))
        importRulePreference = findPreference(getString(R.string.key_pref_import_rules))
        importIfwRulePreference = findPreference(getString(R.string.key_pref_import_ifw_rules))
        exportIfwRulePreference = findPreference(getString(R.string.key_pref_export_ifw_rules))
        resetIfwPreference = findPreference(getString(R.string.key_pref_reset_ifw_rules))
        importMatRulesPreference = findPreference(getString(R.string.key_pref_import_mat_rules))
        aboutPreference = findPreference(getString(R.string.key_pref_about))
        storagePreference = findPreference(getString(R.string.key_pref_save_folder_path))
        backupSystemAppPreference = findPreference(getString(R.string.key_pref_backup_system_apps))
        restoreSystemAppPreference =
            findPreference(getString(R.string.key_pref_restore_system_apps))
        groupPreference = findPreference(getString(R.string.key_pref_group))
        reportPreference = findPreference(getString(R.string.key_pref_report_issue))
        repoPreference = findPreference(getString(R.string.key_pref_rules_repo))
        updateRulePreference = findPreference(getString(R.string.key_pref_update_online_db))
    }

    private fun initPreference() {
        controllerTypePreference?.setDefaultValue(
            getString(R.string.key_pref_controller_type_default_value)
        )
        updateFolderSummary()
    }

    private fun updateFolderSummary() {
        val uri = PreferenceUtil.getSavedRulePath(requireContext())
        // Hasn't set the dir to store
        if (uri == null) {
            storagePreference?.summary = getString(R.string.directory_invalid_or_not_set)
            return
        }
        val folder = try {
            DocumentFile.fromTreeUri(requireContext(), uri)
        } catch (e: Exception) {
            logger.e("Invalid Uri $uri", e)
            null
        }
        // Folder may be unreachable
        val isFolderUnreachable = (folder == null) || !folder.canRead() || !folder.canWrite()
        val summary = if (isFolderUnreachable) {
            getString(R.string.directory_invalid_or_not_set)
        } else {
            uri.path
        }
        storagePreference?.summary = summary
    }

    private fun initListener() {
        storagePreference?.onPreferenceClickListener = this
        onlineRuleSourcePreference?.onPreferenceChangeListener = this
        exportRulePreference?.onPreferenceClickListener = this
        importRulePreference?.onPreferenceClickListener = this
        exportIfwRulePreference?.onPreferenceClickListener = this
        importIfwRulePreference?.onPreferenceClickListener = this
        importMatRulesPreference?.onPreferenceClickListener = this
        resetIfwPreference?.onPreferenceClickListener = this
        aboutPreference?.onPreferenceClickListener = this
        storagePreference?.onPreferenceChangeListener = this
        groupPreference?.onPreferenceClickListener = this
        reportPreference?.onPreferenceClickListener = this
        repoPreference?.onPreferenceClickListener = this
        updateRulePreference?.onPreferenceClickListener = this
    }

    private fun selectFolder() {
        val pm = context?.packageManager ?: return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (intent.resolveActivity(pm) != null) {
            getFolderResult.launch(null)
        } else {
            ToastUtil.showToast(getString(R.string.file_manager_required))
        }
    }

    private fun selectMatFile() {
        val pm = context?.packageManager ?: return
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        if (intent.resolveActivity(pm) != null) {
            importMatResult.launch(arrayOf("*/*"))
            ToastUtil.showToast(R.string.please_select_mat_files, Toast.LENGTH_LONG)
        } else {
            ToastUtil.showToast(getString(R.string.file_manager_required))
        }
    }

    private fun showAbout() {
        BrowserUtil.openUrl(requireContext(), ABOUT_URL)
    }

    private fun showDiscussionGroup() {
        BrowserUtil.openUrl(requireContext(), GROUP_URL)
    }

    private fun showRulesRepo() {
        BrowserUtil.openUrl(requireContext(), RULE_REPO_URL)
    }

    private fun reportIssue() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val baseLogDir = requireContext().filesDir.resolve("log")
            val zippedLog = requireContext().filesDir.resolve("log.zip")
            try {
                if (!baseLogDir.exists()) {
                    baseLogDir.mkdirs()
                    logger.e("Log directory doesn't exist")
                    ToastUtil.showToast(R.string.failed_to_send_log, Toast.LENGTH_LONG)
                    return@launch
                }
                LogUtils.compress(baseLogDir.toString(), zippedLog.toString())
            } catch (e: Exception) {
                logger.e("Failed to compress log", e)
                ToastUtil.showToast(R.string.failed_to_send_log, Toast.LENGTH_LONG)
                return@launch
            }
            ShareUtil.shareFileToEmail(requireContext(), zippedLog)
        }
    }

    private fun setOnlineSourceType(context: Context, type: OnlineSourceType) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(
                context.getString(R.string.key_pref_online_source_type),
                type.name
            )
            .apply()
    }

    companion object {
        private const val ABOUT_URL = "https://github.com/lihenggui/blocker"
        private const val GROUP_URL = "https://t.me/blockerandroid"
        private const val RULE_REPO_URL = "https://github.com/lihenggui/blocker-general-rules"
    }
}
