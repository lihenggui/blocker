package com.merxury.blocker.ui.home.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.elvishew.xlog.LogUtils
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.work.ExportBlockerRulesWork
import com.merxury.blocker.work.ExportIfwRulesWork
import com.merxury.blocker.work.ImportBlockerRuleWork
import com.merxury.blocker.work.ImportIfwRulesWork
import com.merxury.blocker.work.ImportMatRulesWork
import com.merxury.blocker.work.ResetIfwWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener {
    private val logger = XLog.tag("PreferenceFragment")
    private lateinit var sp: SharedPreferences

    private var controllerTypePreference: Preference? = null
    private var exportRulePreference: Preference? = null
    private var importRulePreference: Preference? = null
    private var exportIfwRulePreference: Preference? = null
    private var importIfwRulePreference: Preference? = null
    private var resetIfwPreference: Preference? = null
    private var importMatRulesPreference: Preference? = null
    private var aboutPreference: Preference? = null
    private var storagePreference: Preference? = null
    private var backupSystemAppPreference: SwitchPreference? = null
    private var groupPreference: Preference? = null
    private var reportPreference: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

    private fun importBlockerRule() {
        val importWork = OneTimeWorkRequestBuilder<ImportBlockerRuleWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ImportBlockerRule", ExistingWorkPolicy.KEEP, importWork)
        ToastUtil.showToast(R.string.import_app_rules_please_wait, Toast.LENGTH_LONG)
    }

    private fun exportBlockerRule() {
        val exportWork = OneTimeWorkRequestBuilder<ExportBlockerRulesWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ExportBlockerRule", ExistingWorkPolicy.KEEP, exportWork)
        ToastUtil.showToast(R.string.backing_up_apps_please_wait, Toast.LENGTH_LONG)
    }

    private fun exportIfwRule() {
        ToastUtil.showToast(R.string.backing_up_ifw_please_wait, Toast.LENGTH_LONG)
        val exportWork = OneTimeWorkRequestBuilder<ExportIfwRulesWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ExportIfwRule", ExistingWorkPolicy.KEEP, exportWork)
    }

    private fun importIfwRule() {
        ToastUtil.showToast(R.string.import_ifw_please_wait, Toast.LENGTH_LONG)
        val exportWork = OneTimeWorkRequestBuilder<ImportIfwRulesWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ImportIfwRule", ExistingWorkPolicy.KEEP, exportWork)
    }

    private fun resetIfw() {
        ToastUtil.showToast(R.string.reset_ifw_please_wait, Toast.LENGTH_LONG)
        val exportWork = OneTimeWorkRequestBuilder<ResetIfwWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ResetIfw", ExistingWorkPolicy.KEEP, exportWork)
    }

    private fun importMatRule(fileUri: Uri) {
        ToastUtil.showToast(R.string.import_mat_rule_please_wait, Toast.LENGTH_LONG)
        val data = Data.Builder()
            .putString(ImportMatRulesWork.KEY_FILE_URI, fileUri.toString())
            .build()
        val exportWork = OneTimeWorkRequestBuilder<ImportMatRulesWork>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ImportMatRule", ExistingWorkPolicy.KEEP, exportWork)
    }

    private fun findPreference() {
        controllerTypePreference = findPreference(getString(R.string.key_pref_controller_type))
        exportRulePreference = findPreference(getString(R.string.key_pref_export_rules))
        importRulePreference = findPreference(getString(R.string.key_pref_import_rules))
        importIfwRulePreference = findPreference(getString(R.string.key_pref_import_ifw_rules))
        exportIfwRulePreference = findPreference(getString(R.string.key_pref_export_ifw_rules))
        resetIfwPreference = findPreference(getString(R.string.key_pref_reset_ifw_rules))
        importMatRulesPreference = findPreference(getString(R.string.key_pref_import_mat_rules))
        aboutPreference = findPreference(getString(R.string.key_pref_about))
        storagePreference = findPreference(getString(R.string.key_pref_save_folder_path))
        backupSystemAppPreference = findPreference(getString(R.string.key_pref_backup_system_apps))
        groupPreference = findPreference(getString(R.string.key_pref_group))
        reportPreference = findPreference(getString(R.string.key_pref_report_issue))
    }

    private fun initPreference() {
        controllerTypePreference?.setDefaultValue(getString(R.string.key_pref_controller_type_default_value))
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
        openUrl(ABOUT_URL)
    }

    private fun showDiscussionGroup() {
        openUrl(GROUP_URL)
    }

    private fun openUrl(url: String) {
        val chromeIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        val resolveInfo =
            requireContext().packageManager.queryIntentActivities(chromeIntent.intent, 0)
        if (resolveInfo.isNotEmpty()) {
            logger.i("Open url in Chrome Tabs $url")
            chromeIntent.launchUrl(requireContext(), Uri.parse(url))
        } else {
            val browseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (browseIntent.resolveActivity(requireContext().packageManager) != null) {
                logger.i("Open url in default browser $url")
                startActivity(browseIntent)
            } else {
                logger.w("No browser to open url $url")
                ToastUtil.showToast(getString(R.string.browser_required))
            }
        }
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
            val emailIntent = Intent(Intent.ACTION_SEND)
                .setType("vnd.android.cursor.dir/email")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf("mercuryleee@gmail.com"))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject_template))
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.report_content_template))
            val logUri = FileProvider.getUriForFile(
                requireContext(),
                "com.merxury.blocker.provider",
                zippedLog
            )
            emailIntent.putExtra(Intent.EXTRA_STREAM, logUri)
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)))
        }
    }

    companion object {
        private const val ABOUT_URL = "https://github.com/lihenggui/blocker"
        private const val GROUP_URL = "https://t.me/blockerandroid"
    }
}