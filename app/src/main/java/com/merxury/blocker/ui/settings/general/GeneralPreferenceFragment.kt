package com.merxury.blocker.ui.settings.general

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
import com.merxury.blocker.ui.settings.SettingsActivity

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class GeneralPreferenceFragment : PreferenceFragment() {

    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_general)
        setHasOptionsMenu(true)
        prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val preference = initPrefDefaultValue()
        bindPreferenceSummaryToValue(preference)
        initListener()
    }

    private fun initPrefDefaultValue(): Preference {
        val preference = findPreference(KEY_PREF_CONTROLLER_TYPE)
        preference.setDefaultValue(KEY_PREF_CONTROLLER_TYPE_DEFAULT)
        return preference
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
        const val KEY_PREF_CONTROLLER_TYPE = "pref_controllerType"
        const val KEY_PREF_CONTROLLER_TYPE_DEFAULT = "root"
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