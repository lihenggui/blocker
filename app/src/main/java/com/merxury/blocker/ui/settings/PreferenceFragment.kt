package com.merxury.blocker.ui.settings

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import com.merxury.blocker.R
import com.merxury.blocker.util.ToastUtil

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class PreferenceFragment : PreferenceFragment(), SettingsContract.SettingsView, OnPreferenceClickListener {

    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var prefs: SharedPreferences
    private lateinit var presenter: SettingsPresenter

    private lateinit var controllerTypePreference: Preference
    private lateinit var rulePathPreference: Preference
    private lateinit var exportRulePreference: Preference
    private lateinit var importRulePreference: Preference
    private lateinit var ifwRulePathPreference: Preference
    private lateinit var exportIfwRulePreference: Preference
    private lateinit var importIfwRulePreference: Preference
    private lateinit var resetIfwPreference: Preference
    private lateinit var importMatRulesPreference: Preference
    private lateinit var aboutPreference: Preference

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        initPresenter(context!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        setHasOptionsMenu(true)
        prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        findPreference()
        initPreference()
        initListener()
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
        aboutPreference = findPreference(getString(R.string.key_pref_about))
    }

    private fun initPreference() {
        controllerTypePreference.setDefaultValue(getString(R.string.key_pref_controller_type_default_value))
        bindPreferenceSummaryToValue(controllerTypePreference)
        rulePathPreference.setDefaultValue(getString(R.string.key_pref_rule_path_default_value))
        bindPreferenceSummaryToValue(rulePathPreference)
        ifwRulePathPreference.setDefaultValue(getString(R.string.key_pref_ifw_rule_path_default_value))
        bindPreferenceSummaryToValue(ifwRulePathPreference)
    }

    private fun initPresenter(context: Context) {
        presenter = SettingsPresenter(context, this)
    }

    private fun initListener() {
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            // TODO add later
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        exportRulePreference.onPreferenceClickListener = this
        importRulePreference.onPreferenceClickListener = this
        exportIfwRulePreference.onPreferenceClickListener = this
        importIfwRulePreference.onPreferenceClickListener = this
        importMatRulesPreference.onPreferenceClickListener = this
        resetIfwPreference.onPreferenceClickListener = this
        aboutPreference.onPreferenceClickListener = this
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
        ToastUtil.showToast(getString(res), Toast.LENGTH_SHORT)
    }

    override fun showDialog(title: String, message: String, action: () -> Unit) {
        activity?.let {
            AlertDialog.Builder(it)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton(R.string.ok) { _, _ -> action() }
                    .create()
                    .show()
        }
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        if (preference == null) {
            return false
        }
        Log.d(TAG, "onPreferenceClick: ${preference.key}")
        when (preference) {
            exportRulePreference -> showDialog(getString(R.string.warning), getString(R.string.export_all_rules_warning_message), presenter::exportAllRules)
            importRulePreference -> showDialog(getString(R.string.warning), getString(R.string.import_all_rules_warning_message), presenter::importAllRules)
            exportIfwRulePreference -> showDialog(getString(R.string.warning), getString(R.string.export_all_ifw_rules_warning_message), presenter::exportAllIfwRules)
            importIfwRulePreference -> showDialog(getString(R.string.warning), getString(R.string.import_all_ifw_rules_warning_message), presenter::importAllIfwRules)
            importMatRulesPreference -> showDialog(getString(R.string.warning), getString(R.string.import_all_rules_warning_message), presenter::importMatRules)
            resetIfwPreference -> showDialog(getString(R.string.warning), getString(R.string.reset_ifw_warning_message), presenter::resetIFW)
            aboutPreference -> {
                // TODO add about action
            }
            else -> return false
        }
        return true

    }

    companion object {
        private const val TAG = "PreferenceFragment"

        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            val stringValue = value.toString()
            if (preference is ListPreference) {
                val index = preference.findIndexOfValue(stringValue)
                preference.setSummary(
                        if (index >= 0)
                            preference.entries[index]
                        else
                            null)

            } else {
                preference.summary = stringValue
            }
            true
        }

        private fun bindPreferenceSummaryToValue(preference: Preference) {
            preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.context)
                            .getString(preference.key, ""))
        }
    }
}