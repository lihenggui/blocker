package com.merxury.blocker.core

import android.content.Context
import android.content.pm.ComponentInfo
import com.merxury.blocker.core.ifw.IfwController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.root.RootController
import com.merxury.blocker.core.shizuku.ShizukuController

/**
 * Created by Mercury on 2018/3/10.
 */

class ComponentControllerProxy private constructor(method: EControllerMethod, context: Context) : IController {

    private var controller: IController = when (method) {
        EControllerMethod.IFW -> IfwController(context)
        EControllerMethod.PM -> RootController(context)
        EControllerMethod.SHIZUKU -> ShizukuController(context)
    }

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        return controller.switchComponent(packageName, componentName, state)
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        return controller.enable(packageName, componentName)
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        return controller.disable(packageName, componentName)
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return controller.checkComponentEnableState(packageName, componentName)
    }

    override fun batchEnable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        return controller.batchEnable(componentList) { action(it) }
    }

    override fun batchDisable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        return controller.batchDisable(componentList) { action(it) }
    }

    companion object {
        @Volatile
        private var instance: IController? = null
        var controllerMethod: EControllerMethod? = null

        fun getInstance(method: EControllerMethod, context: Context): IController =
                synchronized(this) {
                    if (method != controllerMethod) {
                        getComponentControllerProxy(method, context)
                    } else {
                        instance ?: getComponentControllerProxy(method, context)
                    }
                }

        private fun getComponentControllerProxy(method: EControllerMethod, context: Context): ComponentControllerProxy {
            return ComponentControllerProxy(method, context).also {
                controllerMethod = method
                instance = it
            }
        }
    }
}
