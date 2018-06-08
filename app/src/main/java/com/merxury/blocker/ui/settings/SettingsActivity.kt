package com.merxury.blocker.ui.settings

import android.os.Bundle

class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, PreferenceFragment())
                .commit()
    }
}