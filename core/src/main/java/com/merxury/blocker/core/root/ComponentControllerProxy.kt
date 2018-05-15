package com.merxury.blocker.core.root

import android.content.pm.ComponentInfo
import com.merxury.blocker.core.IController

/**
 * Created by Mercury on 2018/3/10.
 */

class ComponentControllerProxy private constructor(method: EControllerMethod) : IController {

    private lateinit var controller: IController
    private lateinit var controllerMethod: EControllerMethod

    init {
        when (method) {
            EControllerMethod.PM -> controller = RootController()
        }
    }


    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        return controller.switchComponent(packageName, componentName, state)
    }

    override fun enableComponent(componentInfo: ComponentInfo): Boolean {
        return controller.enableComponent(componentInfo)
    }

    override fun disableComponent(componentInfo: ComponentInfo): Boolean {
        return controller.disableComponent(componentInfo)
    }

    companion object {
        @Volatile
        var instance: ComponentControllerProxy? = null

        fun getInstance(method: EControllerMethod): IController =
                instance ?: synchronized(this) {
                    instance ?: ComponentControllerProxy(method).also {
                        it.controllerMethod = method
                        instance = it
                    }
                }

    }
}
