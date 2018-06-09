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
class PreferenceFragment : PreferenceFragment() {

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
        val controllerPref = findPreference(KEY_PREF_CONTROLLER_TYPE)
        controllerPref.setDefaultValue(KEY_PREF_CONTROLLER_TYPE_DEFAULT)
        bindPreferenceSummaryToValue(controllerPref)
        val ruleFolderPref = findPreference(KEY_PREF_RULE_PATH)
        ruleFolderPref.setDefaultValue(KEY_PREF_RULE_PATH_DEFAULT)
        bindPreferenceSummaryToValue(ruleFolderPref)
        val ifwFolderPref = findPreference(KEY_PREF_IFW_RULE_PATH)
        ifwFolderPref.setDefaultValue(KEY_PREF_IFW_RULE_PATH_DEFAULT)
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

    companion object {
        // TODO put in strings.xml later
        const val KEY_PREF_CONTROLLER_TYPE = "pref_controllerType"
        const val KEY_PREF_CONTROLLER_TYPE_DEFAULT = "root"
        const val KEY_PREF_RULE_PATH = "pref_key_rule_path"
        const val KEY_PREF_RULE_PATH_DEFAULT = "/Blocker/rules/"
        const val KEY_PREF_IFW_RULE_PATH = "pref_key_ifw_rule_path"
        const val KEY_PREF_IFW_RULE_PATH_DEFAULT = "/Blocker/ifw/"
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