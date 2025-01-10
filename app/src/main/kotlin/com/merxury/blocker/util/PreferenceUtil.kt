/*
 * Copyright 2025 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.util

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.merxury.blocker.R
import com.merxury.blocker.core.model.data.ControllerType

object PreferenceUtil {
    fun getControllerType(context: Context): ControllerType {
        // Magic value, but still use it.
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return when (
            pref.getString(
                context.getString(R.string.key_pref_controller_type),
                context.getString(R.string.key_pref_controller_type_default_value),
            )
        ) {
            "pm" -> ControllerType.PM
            "shizuku" -> ControllerType.SHIZUKU
            else -> ControllerType.IFW
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
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.key_pref_rule_path), uri?.toString()).apply()
    }

    fun shouldBackupSystemApps(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(context.getString(R.string.key_pref_backup_system_apps), false)
    }

    fun shouldRestoreSystemApps(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(context.getString(R.string.key_pref_restore_system_apps), false)
    }

    fun setShowSystemApps(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.key_pref_show_system_apps), value).apply()
    }

    fun getShowSystemApps(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(context.getString(R.string.key_pref_show_system_apps), false)
    }

    fun setShowServiceInfo(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.key_pref_show_running_service_info), value)
            .apply()
    }

    fun getShowServiceInfo(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(
            context.getString(R.string.key_pref_show_running_service_info),
            false,
        )
    }

    fun setSearchSystemApps(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.key_pref_search_system_apps), value).apply()
    }

    fun getSearchSystemApps(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(
            context.getString(R.string.key_pref_search_system_apps),
            false,
        )
    }

    fun setShowEnabledComponentShowFirst(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(
            context.getString(R.string.key_pref_show_enabled_component_show_first),
            value,
        ).apply()
    }

    fun getShowEnabledComponentShowFirst(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getBoolean(
            context.getString(R.string.key_pref_show_enabled_component_show_first),
            false,
        )
    }
}
