package com.merxury.blocker.core

import android.content.Context
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.root.RootController
import com.merxury.blocker.core.shizuku.ShizukuController


/**
 * Created by Mercury on 2018/3/10.
 */

class ComponentControllerProxy private constructor(method: EControllerMethod, context: Context) : IController {

    private lateinit var controller: IController

    init {
        when (method) {
            EControllerMethod.PM -> controller = RootController(context)
            EControllerMethod.SHIZUKU -> controller = ShizukuController(context)
        }
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

    companion object {
        @Volatile
        var instance: IController? = null
        var controllerMethod: EControllerMethod? = null

        fun getInstance(method: EControllerMethod, context: Context): IController =
                if (method != controllerMethod) {
                    instance ?: synchronized(this) {
                        instance
                                ?: ComponentControllerProxy(method, context).also {
                                    controllerMethod = method
                                    instance = it
                                }
                    }
                } else {
                    instance!!
                }

    }
}
