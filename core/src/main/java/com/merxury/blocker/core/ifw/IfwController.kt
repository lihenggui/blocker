package com.merxury.blocker.core.ifw

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.runBlocking

class IfwController(val context: Context) : IController {
    private lateinit var controller: IntentFirewall
    private lateinit var packageInfo: PackageInfo

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        init(packageName)
        val type = getComponentType(packageName, componentName)
        if (type == ComponentType.PROVIDER) {
            return when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> ComponentControllerProxy.getInstance(EControllerMethod.PM, context).disable(packageName, componentName)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> ComponentControllerProxy.getInstance(EControllerMethod.PM, context).enable(packageName, componentName)
                else -> false
            }
        }
        val result = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> controller.add(packageName, componentName, type)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> controller.remove(packageName, componentName, type)
            else -> false
        }
        if (result) {
            try {
                controller.save()
            }catch (e: Exception) {
                controller.removeCache()
                throw e
            }
        }
        return result;
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    override fun batchEnable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        var succeededCount = 0
        if (componentList.isEmpty()) {
            return succeededCount
        }
        componentList.forEach {
            init(it.packageName)
            val type = getComponentType(it.packageName, it.name)
            if (controller.remove(it.packageName, it.name, type)) {
                succeededCount++
            }
            action(it)
        }
        controller.save()
        return succeededCount
    }

    override fun batchDisable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        var succeededCount = 0
        if (componentList.isEmpty()) {
            return succeededCount
        }
        componentList.forEach {
            init(it.packageName)
            val type = getComponentType(it.packageName, it.name)
            if (controller.add(it.packageName, it.name, type)) {
                succeededCount++
            }
            action(it)
        }
        controller.save()
        return succeededCount
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        init(packageName)
        return controller.getComponentEnableState(packageName, componentName)
    }

    private fun init(packageName: String) {
        initController(packageName)
        runBlocking {
            // TODO Will remove this blocking call
            initPackageInfo(packageName)
        }
    }

    private fun initController(packageName: String) {
        if (!::controller.isInitialized || controller.packageName != packageName) {
            controller = IntentFirewallImpl.getInstance(context, packageName)
            return
        }
    }

    private suspend fun initPackageInfo(packageName: String) {
        if (!::packageInfo.isInitialized || packageInfo.packageName != packageName) {
            packageInfo =
                ApplicationUtil.getApplicationComponents(context.packageManager, packageName)
        }
    }

    private fun getComponentType(packageName: String, componentName: String): ComponentType {
        packageInfo.receivers?.forEach {
            if (it.name == componentName) {
                return ComponentType.BROADCAST
            }
        }
        packageInfo.services?.forEach {
            if (it.name == componentName) {
                return ComponentType.SERVICE
            }
        }
        packageInfo.activities?.forEach {
            if (it.name == componentName) {
                return ComponentType.ACTIVITY
            }
        }
        packageInfo.providers?.forEach {
            if (it.name == componentName) {
                return ComponentType.PROVIDER
            }
        }
        return ComponentType.UNKNOWN
    }
}