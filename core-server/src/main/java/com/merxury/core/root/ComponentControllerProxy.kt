package com.merxury.core.root

import android.content.Context
import android.support.annotation.IntDef
import com.merxury.core.IController

/**
 * Created by Mercury on 2018/3/10.
 */

class ComponentControllerProxy private constructor(@ComponentControllerProxy.Companion.ControllerMethod method: Int, context: Context) : IController {

    init {
        TODO("initialize new root server")
        when (method) {
            PM -> controller = RootController.getInstance()
            RIKKA -> controller = RikkaServerController.getInstance(context)
        }
    }

    private val controller: IController

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        return controller.switchComponent(packageName, componentName, state)
    }

    companion object {
        @IntDef(PM, ROOT_SERVER, RIKKA, XML)
        @Retention(AnnotationRetention.SOURCE)
        annotation class ControllerMethod

        const val PM = 1;
        const val ROOT_SERVER = 2;
        const val RIKKA = 3;
        const val XML = 4;

        fun newInstance(@ControllerMethod method: Int, context: Context) {
            return
        }
    }
}
