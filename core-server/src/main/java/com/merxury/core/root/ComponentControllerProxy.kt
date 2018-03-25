package com.merxury.core.root

import android.content.Context
import android.content.pm.ComponentInfo
import com.merxury.core.IController

/**
 * Created by Mercury on 2018/3/10.
 */

class ComponentControllerProxy private constructor(method: EControllerMethod, context: Context?) : IController {

    private lateinit var controller: IController
    private lateinit var controllerMethod: EControllerMethod

    init {
        when (method) {
            EControllerMethod.PM -> controller = RootController()
        //EControllerMethod.SHIZUKU -> controller = RikkaServerController.getInstance(context)
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

        fun getInstance(method: EControllerMethod, context: Context?): IController =
                instance ?: synchronized(this) {
                    instance ?: ComponentControllerProxy(method, context).also {
                        it.controllerMethod = method
                        instance = it
                    }
                }

    }
}
