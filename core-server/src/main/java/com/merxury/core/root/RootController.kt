package com.merxury.core.root

import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.util.Log

import com.merxury.core.IController
import com.stericson.RootShell.exceptions.RootDeniedException
import com.stericson.RootTools.RootTools

import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

class RootController : IController {

    init {
        RootTools.debugMode = true
    }

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        val comm: String = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> String.format(ENABLE_COMPONENT_TEMPLATE, packageName, componentName)
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName)
            else -> return false
        }
        Log.d(TAG, "command:$comm, componentState is $state")
        try {
            val commandOutput = RootCommand.runBlockingCommand(comm)
            return !commandOutput.contains(FAILED_EXCEPTION_MSG)
        } catch (e: RootDeniedException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
            return false
        } catch (e: TimeoutException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
            return false
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
            return false
        }

    }

    override fun enableComponent(componentInfo: ComponentInfo): Boolean {
        return switchComponent(componentInfo.packageName, componentInfo.name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override fun disableComponent(componentInfo: ComponentInfo): Boolean {
        return switchComponent(componentInfo.packageName, componentInfo.name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    companion object {
        private const val TAG = "RootController"
        private const val DISABLE_COMPONENT_TEMPLATE = "pm disable %s/%s"
        private const val ENABLE_COMPONENT_TEMPLATE = "pm enable %s/%s"
        private const val FAILED_EXCEPTION_MSG = "java.lang.IllegalArgumentException"
    }
}
