package com.merxury.blocker.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.utils.FileUtils

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class PreferenceFragment : PreferenceFragmentCompat(), SettingsContract.SettingsView,
    Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    private val logger = XLog.tag("PreferenceFragment")
    private lateinit var prefs: SharedPreferences
    private lateinit var presenter: SettingsPresenter

    private var controllerTypePreference: Preference? = null
    private var exportRulePreference: Preference? = null
    private var importRulePreference: Preference? = null
    private var exportIfwRulePreference: Preference? = null
    private var importIfwRulePreference: Preference? = null
    private var resetIfwPreference: Preference? = null
    private var importMatRulesPreference: Preference? = null
    private var aboutPreference: Preference? = null
    private var storagePreference: Preference? = null

    private val matRulePathRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        findPreference()
        initPreference()
        initListener()
        initPresenter()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        logger.d("Preference: $preference changed, value = $newValue")
        return true
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
    }

    private fun initPreference() {
        controllerTypePreference?.setDefaultValue(getString(R.string.key_pref_controller_type_default_value))
        storagePreference?.summaryProvider = Preference.SummaryProvider<Preference> {
            val path = getRulePath()
            path?.toString() ?: getString(R.string.folder_not_set)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            matRulePathRequestCode -> {
                if (resultCode == Activity.RESULT_OK) {
                    val filePath = FileUtils.getUriPath(requireContext(), data?.data)
                    showDialog(
                        getString(R.string.warning),
                        getString(R.string.import_all_rules_warning_message),
                        filePath
                    ) {
                        presenter.importMatRules(it)
                    }
                }
            }
            REQUEST_CODE_ASSIGN_FOLDER -> handleAssignFolder(data)
        }
    }


    private fun assignFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_CODE_ASSIGN_FOLDER)
    }

    private fun handleAssignFolder(data: Intent?) {
        if (data == null) {
            logger.e("Intent data is null")
            return
        }
        val uri = data.data ?: run {
            logger.e("Null folder assigned")
            return
        }
        val desiredPermission =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flags = data.flags and desiredPermission
        requireActivity().contentResolver.takePersistableUriPermission(uri, flags)
        logger.d("Assigned ${uri.path} with FLAG_GRANT_READ_URI_PERMISSION and FLAG_GRANT_WRITE_URI_PERMISSION")
        storeRulePath(uri)
    }

    private fun storeRulePath(uri: Uri) {
        prefs.edit { putString(getString(R.string.key_pref_rule_path), uri.path) }
    }

    private fun getRulePath(): Uri? {
        val storedPath = prefs.getString(getString(R.string.key_pref_rule_path), null)
        if (storedPath.isNullOrEmpty()) return null
        return Uri.parse(storedPath)
    }

    private fun initPresenter() {
        presenter = SettingsPresenter(requireContext(), this)
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

    override fun showExportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int) {

    }

    override fun showImportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int) {

    }

    override fun showResetResult(isSucceed: Boolean) {

    }

    override fun showMessage(res: Int) {
        ToastUtil.showToast(res, Toast.LENGTH_SHORT)
    }

    override fun showDialog(title: String, message: String, action: () -> Unit) {

    }

    override fun showDialog(
        title: String,
        message: String,
        file: String?,
        action: (file: String?) -> Unit
    ) {
        AlertDialog.Builder(requireActivity())
            .setTitle(title)
            .setMessage(message)
            .setCancelable(true)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.ok) { _, _ -> action(file) }
            .create()
            .show()
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        if (preference == null) {
            return false
        }
        logger.d("onPreferenceClick: ${preference.key}")
        when (preference) {
            exportRulePreference -> showDialog(
                getString(R.string.warning),
                getString(R.string.export_all_rules_warning_message)
            ) {
                presenter.exportAllRules()
            }
            importRulePreference -> showDialog(
                getString(R.string.warning),
                getString(R.string.import_all_rules_warning_message)
            ) {
                presenter.importAllRules()
            }
            exportIfwRulePreference -> showDialog(
                getString(R.string.warning),
                getString(R.string.export_all_ifw_rules_warning_message)
            ) {
                presenter.exportAllIfwRules()
            }

            importIfwRulePreference -> showDialog(
                getString(R.string.warning),
                getString(R.string.import_all_ifw_rules_warning_message)
            ) {
                presenter.importAllIfwRules()
            }
            importMatRulesPreference -> selectMatFile()
            resetIfwPreference -> showDialog(
                getString(R.string.warning),
                getString(R.string.reset_ifw_warning_message)
            ) {
                presenter.resetIFW()
            }
            aboutPreference -> showAbout()
            storagePreference -> assignFolder()
            else -> return false
        }
        return true
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
        private const val REQUEST_CODE_ASSIGN_FOLDER = 1000
    }
}