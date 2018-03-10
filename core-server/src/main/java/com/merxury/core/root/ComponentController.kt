package com.merxury.core.root

import android.support.annotation.IntDef
import com.merxury.core.IController

/**
 * Created by Mercury on 2018/3/10.
 */
class ComponentController : IController {


    private constructor(method : Int) {

    }
    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {

    }
    companion object {
        @IntDef(PM, ROOT_SERVER, RIKKA, XML)
        @Retention(AnnotationRetention.SOURCE)
        annotation class ControllerMethod

        const val PM = 1;
        const val ROOT_SERVER = 2;
        const val RIKKA = 3;
        const val XML = 4;
        var controllerMethod = 0;
        fun newInstance(@ControllerMethod method : Int) {
            controllerMethod = method;
        }
    }
}
