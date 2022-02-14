package com.merxury.blocker.util

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.merxury.blocker.R
import com.merxury.blocker.core.root.EControllerMethod

object PreferenceUtil {
    fun getControllerType(context: Context): EControllerMethod {
        // Magic value, but still use it.
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return when (pref.getString(
            context.getString(R.string.key_pref_controller_type),
            context.getString(R.string.key_pref_controller_type_default_value)
        )) {
            "pm" -> EControllerMethod.PM
            "shizuku" -> EControllerMethod.SHIZUKU
            else -> EControllerMethod.IFW
        }
    }

    fun getSavedRulePath(context: Context): Uri? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val storedPath = pref.getString(context.getString(R.string.key_pref_rule_path), null)
        if (storedPath.isNullOrEmpty()) return null
        return Uri.parse(storedPath)
    }

    fun getIfwRulePath(context: Context): Uri? {
        return getSavedRulePath(context)?.buildUpon()?.appendPath("ifw")?.build()
    }

    fun setRulePath(context: Context, uri: Uri?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(R.string.key_pref_rule_path), uri?.toString())
            .apply()

    }

    fun shouldBackupSystemApps(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(context.getString(R.string.key_pref_backup_system_apps), false)
    }

    fun shouldLoadSystemApps(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(context.getString(R.string.key_pref_load_system_apps), false)
    }
}