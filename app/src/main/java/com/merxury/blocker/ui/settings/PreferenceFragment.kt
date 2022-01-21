package com.merxury.blocker.ui.settings

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.browser.customtabs.CustomTabsIntent
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.work.ExportBlockerRulesWork
import com.merxury.blocker.work.ImportAllRuleWork

class PreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener,
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

    private val matRulePathRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        sp = PreferenceManager.getDefaultSharedPreferences(activity)
        findPreference()
        initPreference()
        initListener()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        logger.d("Preference: ${preference?.key} changed, value = $newValue")
        return true
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        logger.d("Preference: ${preference?.key} clicked")
        when (preference) {
            storagePreference -> setFolderToSave()
            exportRulePreference -> exportBlockerRule()
            importRulePreference -> importBlockerRule()
            importMatRulesPreference -> selectMatFile()
            aboutPreference -> showAbout()
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == PERMISSION_REQUEST_CODE) {
            if (data != null) {
                val uri = data.data ?: return
                logger.d("Save folder: $uri")
                val flags = data.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                requireActivity().contentResolver.takePersistableUriPermission(uri, flags)
                PreferenceUtil.setRulePath(requireContext(), data.data)
                updateFolderSummary()
            }
        }
    }

    private fun setFolderToSave() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, PERMISSION_REQUEST_CODE)
    }

    private fun importBlockerRule() {
        val importBlockerRuleReq = OneTimeWorkRequestBuilder<ImportAllRuleWork>().build()
        WorkManager.getInstance(requireContext())
            .enqueue(importBlockerRuleReq)
    }

    private fun exportBlockerRule() {
        val exportRequest = OneTimeWorkRequestBuilder<ExportBlockerRulesWork>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("ExportBlockerRule", ExistingWorkPolicy.KEEP, exportRequest)
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
        val folder = DocumentFile.fromTreeUri(requireContext(), uri)
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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            startActivity(Intent(activity, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectMatFile() {
        val pm = context?.packageManager ?: return
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        if (intent.resolveActivity(pm) != null) {
            startActivityForResult(intent, matRulePathRequestCode)
        } else {
            ToastUtil.showToast(getString(R.string.file_manager_required))
        }
    }

    private fun showAbout() {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(requireContext(), Uri.parse(ABOUT_URL))
    }

    companion object {
        private const val ABOUT_URL = "https://github.com/lihenggui/blocker"
        private const val PERMISSION_REQUEST_CODE = 101
    }
}