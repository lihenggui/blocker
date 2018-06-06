package com.merxury.blocker.ui.settings

import android.os.Bundle
import android.preference.PreferenceActivity
import com.merxury.blocker.R

class SettingsActivity : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
    }
}