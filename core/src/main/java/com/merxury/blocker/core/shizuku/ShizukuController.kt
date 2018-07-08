package com.merxury.blocker.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.IController
import moe.shizuku.api.ShizukuPackageManagerV26

class ShizukuController(val context: Context) : IController {

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        try {
            ShizukuPackageManagerV26.setComponentEnabledSetting(ComponentName(packageName, componentName), state, 0, 0)
        } catch (e: Exception) {
            Log.e(TAG, e.message)
            e.printStackTrace()
            return false
        }
        return true
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    override fun batchEnable(componentList: List<ComponentName>): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun batchDisable(componentList: List<ComponentName>): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationComponents.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))
    }

    companion object {
        private const val TAG = "ShizukuController"
    }
}