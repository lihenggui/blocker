package com.merxury.blocker.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.PreferenceManager
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.work.ScheduledWork
import com.merxury.libkit.utils.FileUtils
import com.tbruyelle.rxpermissions2.RxPermissions
import java.util.concurrent.TimeUnit

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class PreferenceFragment : PreferenceFragmentCompat(), SettingsContract.SettingsView,
    Preference.OnPreferenceClickListener {
    private val logger = XLog.tag("PreferenceFragment")
    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var prefs: SharedPreferences
    private lateinit var presenter: SettingsPresenter

    private var controllerTypePreference: Preference? = null
    private var rulePathPreference: Preference? = null
    private var exportRulePreference: Preference? = null
    private var importRulePreference: Preference? = null
    private var ifwRulePathPreference: Preference? = null
    private var exportIfwRulePreference: Preference? = null
    private var importIfwRulePreference: Preference? = null
    private var resetIfwPreference: Preference? = null
    private var importMatRulesPreference: Preference? = null
    private var autoBlockPreference: CheckBoxPreference? = null
    private var forceDozePreference: CheckBoxPreference? = null
    private var aboutPreference: Preference? = null

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

    private fun findPreference() {
        controllerTypePreference = findPreference(getString(R.string.key_pref_controller_type))
        rulePathPreference = findPreference(getString(R.string.key_pref_rule_path))
        exportRulePreference = findPreference(getString(R.string.key_pref_export_rules))
        importRulePreference = findPreference(getString(R.string.key_pref_import_rules))
        ifwRulePathPreference = findPreference(getString(R.string.key_pref_ifw_rule_path))
        importIfwRulePreference = findPreference(getString(R.string.key_pref_import_ifw_rules))
        exportIfwRulePreference = findPreference(getString(R.string.key_pref_export_ifw_rules))
        resetIfwPreference = findPreference(getString(R.string.key_pref_reset_ifw_rules))
        importMatRulesPreference = findPreference(getString(R.string.key_pref_import_mat_rules))
        autoBlockPreference =
                findPreference(getString(R.string.key_pref_auto_block)) as? CheckBoxPreference
        forceDozePreference =
                findPreference(getString(R.string.key_pref_force_doze)) as? CheckBoxPreference
        aboutPreference = findPreference(getString(R.string.key_pref_about))
    }

    private fun initPreference() {
        controllerTypePreference?.setDefaultValue(getString(R.string.key_pref_controller_type_default_value))
        rulePathPreference?.setDefaultValue(getString(R.string.key_pref_rule_path_default_value))
        bindPreferenceSummaryToValue(rulePathPreference)
        ifwRulePathPreference?.setDefaultValue(getString(R.string.key_pref_ifw_rule_path_default_value))
        bindPreferenceSummaryToValue(ifwRulePathPreference)
    }

    private fun initPresenter() {
        presenter = SettingsPresenter(requireContext(), this)
    }

    private fun initListener() {
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            // TODO add later
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        exportRulePreference?.onPreferenceClickListener = this
        importRulePreference?.onPreferenceClickListener = this
        exportIfwRulePreference?.onPreferenceClickListener = this
        importIfwRulePreference?.onPreferenceClickListener = this
        importMatRulesPreference?.onPreferenceClickListener = this
        resetIfwPreference?.onPreferenceClickListener = this
        autoBlockPreference?.onPreferenceClickListener = this
        forceDozePreference?.onPreferenceClickListener = this
        aboutPreference?.onPreferenceClickListener = this
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

    @SuppressLint("CheckResult")
    override fun showDialog(title: String, message: String, action: () -> Unit) {
        RxPermissions(requireActivity())
            .request(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            .subscribe { result ->
                if (result) {
                    showConfirmationDialog(title, message, action)
                } else {
                    showRequirePermissionDialog()
                }
            }
    }

    override fun showDialog(
        title: String,
        message: String,
        file: String?,
        action: (file: String?) -> Unit
    ) {
        activity?.let {
            AlertDialog.Builder(it)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(R.string.ok) { _, _ -> action(file) }
                .create()
                .show()
        }
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
            autoBlockPreference, forceDozePreference -> initAutoBlockAndDoze()
            aboutPreference -> showAbout()
            else -> return false
        }
        return true
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
        }
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

    private fun initAutoBlockAndDoze() {
        if (autoBlockPreference?.isChecked == false && forceDozePreference?.isChecked == false) {
            logger.d("Canceling scheduled work.")
            WorkManager.getInstance().cancelAllWork()
        } else {
            warnExperimentalFeature()
            val scheduleWork = PeriodicWorkRequest.Builder(
                ScheduledWork::class.java,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
            ).build()
            WorkManager.getInstance().enqueueUniquePeriodicWork(
                SCHEDULED_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                scheduleWork
            )
            logger.d("Scheduled work activated")
        }
    }

    private fun warnExperimentalFeature() {
        context?.let {
            AlertDialog.Builder(it)
                .setTitle(R.string.warning)
                .setMessage(R.string.experimental_features_warning)
                .setPositiveButton(android.R.string.yes) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        action: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setCancelable(true)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.ok) { _, _ -> action() }
            .create()
            .show()
    }

    private fun showRequirePermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.warning)
            .setMessage(R.string.require_permission_message)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    companion object {
        private const val ABOUT_URL = "https://github.com/lihenggui/blocker"
        private const val SCHEDULED_WORK_TAG = "BlockerScheduledWork"

        private val sBindPreferenceSummaryToValueListener =
            Preference.OnPreferenceChangeListener { preference, value ->
                val stringValue = value.toString()
                if (preference is ListPreference) {
                    val index = preference.findIndexOfValue(stringValue)
                    preference.setSummary(
                        if (index >= 0)
                            preference.entries[index]
                        else
                            null
                    )
                } else {
                    preference.summary = stringValue
                }
                true
            }

        private fun bindPreferenceSummaryToValue(preference: Preference?) {
            preference?.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener
            sBindPreferenceSummaryToValueListener.onPreferenceChange(
                preference,
                PreferenceManager
                    .getDefaultSharedPreferences(preference?.context)
                    .getString(preference?.key, "")
            )
        }
    }
}