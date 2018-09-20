package com.merxury.blocker.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.merxury.blocker.core.IController
import com.merxury.libkit.utils.ApplicationUtil
import moe.shizuku.api.ShizukuPackageManagerV26

class ShizukuController(val context: Context) : IController {
    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        ShizukuPackageManagerV26.setComponentEnabledSetting(ComponentName(packageName, componentName), state, 0, 0)
        return true
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    override fun batchEnable(componentList: List<ComponentInfo>): Int {
        var successCount = 0
        componentList.forEach {
            if (enable(it.packageName, it.name)) {
                successCount++
            }
        }
        return successCount
    }

    override fun batchDisable(componentList: List<ComponentInfo>): Int {
        var successCount = 0
        componentList.forEach {
            if (disable(it.packageName, it.name)) {
                successCount++
            }
        }
        return successCount
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationUtil.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))
    }

    companion object {
        private const val TAG = "ShizukuController"
    }
}