package com.merxury.blocker.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.IController
import com.merxury.libkit.utils.ApplicationUtil
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import rikka.sui.Sui

class ShizukuController(val context: Context) : IController {
    private val logger = XLog.tag("ShizukuController").build()
    private var pm: IPackageManager? = null

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        if (!Sui.isSui()) {
            logger.d("Sui is not initialized")
            val result = Sui.init(context.packageName)
            if (result) {
                logger.d("Sui initialized")
            } else {
                logger.d("Sui init failed")
            }
        }
        if (pm == null) {
            pm = IPackageManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("package")
                )
            )
        }
        // 0 means kill the application
        pm?.setComponentEnabledSetting(ComponentName(packageName, componentName), state, 0, 0)
        return true
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    override fun batchEnable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        var successCount = 0
        componentList.forEach {
            if (enable(it.packageName, it.name)) {
                successCount++
            }
            action(it)
        }
        return successCount
    }

    override fun batchDisable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        var successCount = 0
        componentList.forEach {
            if (disable(it.packageName, it.name)) {
                successCount++
            }
            action(it)
        }
        return successCount
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationUtil.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))
    }
}