package com.merxury.blocker.core.ifw

import android.content.ComponentName
import android.content.Context
import com.merxury.blocker.core.IController

class IfwController(context: Context) : IController {
    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun batchEnable(componentList: List<ComponentName>): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun batchDisable(componentList: List<ComponentName>): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}