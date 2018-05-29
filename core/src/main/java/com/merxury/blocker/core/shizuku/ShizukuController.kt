package com.merxury.blocker.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.IController
import moe.shizuku.api.ShizukuPackageManagerV26

class ShizukuController(val context: Context): IController {

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        ShizukuPackageManagerV26.setComponentEnabledSetting(ComponentName(packageName, componentName), state, PackageManager.DONT_KILL_APP, 2000)
        return true
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationComponents.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))
    }

    companion object {
        private const val TAG = "ShizukuController"
    }
}