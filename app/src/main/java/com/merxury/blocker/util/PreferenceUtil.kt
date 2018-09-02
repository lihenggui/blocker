package com.merxury.blocker.util

import android.content.Context
import android.preference.PreferenceManager
import com.merxury.blocker.R
import com.merxury.blocker.core.root.EControllerMethod

object PreferenceUtil {
    fun getControllerType(context: Context): EControllerMethod {
        // Magic value, but still use it.
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return when (pref.getString(context.getString(R.string.key_pref_controller_type), context.getString(R.string.key_pref_controller_type_default_value))) {
            "ifw" -> EControllerMethod.IFW
            else -> EControllerMethod.PM
        }
    }
}