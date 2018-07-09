package com.merxury.blocker.core.ifw

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.IController
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType

class IfwController(val context: Context) : IController {
    private lateinit var controller: IntentFirewall
    private lateinit var packageInfo: PackageInfo

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        initController(packageName)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        initController(packageName)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        initController(packageName)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun batchEnable(componentList: List<ComponentName>): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun batchDisable(componentList: List<ComponentName>): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        initController(packageName)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun initController(packageName: String) {
        synchronized(controller) {
            if (!::controller.isInitialized || controller.packageName != packageName) {
                controller = IntentFirewallImpl.getInstance(context, packageName)
                return
            }
        }
    }

    private fun initPackageInfo(packageName: String) {
        if (!::packageInfo.isInitialized) {
            packageInfo = ApplicationComponents.getApplicationComponents(context.packageManager, packageName)
        }
    }

    private fun matchComponentType(packageName: String, componentName: String): ComponentType {

    }


}