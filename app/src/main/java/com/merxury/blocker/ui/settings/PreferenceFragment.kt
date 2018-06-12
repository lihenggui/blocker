package com.merxury.blocker.ui.settings

import android.annotation.TargetApi
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.view.MenuItem
import com.merxury.blocker.R

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class PreferenceFragment : PreferenceFragment(), SettingsContract.SettingsView {

    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        setHasOptionsMenu(true)
        prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        initPreference()
        initListener()
    }

    private fun initPreference() {
        val controllerPref = findPreference(getString(R.string.key_pref_controller_type))
        controllerPref.setDefaultValue(getString(R.string.key_pref_controller_type_default_value))
        bindPreferenceSummaryToValue(controllerPref)
        val ruleFolderPref = findPreference(getString(R.string.key_pref_rule_path))
        ruleFolderPref.setDefaultValue(getString(R.string.key_pref_rule_path_default_value))
        bindPreferenceSummaryToValue(ruleFolderPref)
        val ifwFolderPref = findPreference(getString(R.string.key_pref_ifw_rule_path))
        ifwFolderPref.setDefaultValue(getString(R.string.key_pref_ifw_rule_path_default_value))
        bindPreferenceSummaryToValue(ifwFolderPref)
    }

    private fun initListener() {
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {

            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
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
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showImportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
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