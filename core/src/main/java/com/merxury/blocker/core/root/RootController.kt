package com.merxury.blocker.core.root

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.IController
import com.stericson.RootTools.RootTools

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

class RootController(val context: Context) : IController {

    init {
        RootTools.debugMode = true
    }

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        val comm: String = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> removeEscapeCharacter(String.format(ENABLE_COMPONENT_TEMPLATE, packageName, componentName))
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> removeEscapeCharacter(String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName))
            else -> return false
        }
        Log.d(TAG, "command:$comm, componentState is $state")
        try {
            val commandOutput = RootCommand.runBlockingCommand(comm)
            Log.d(TAG, "Command output: $commandOutput")
            return !commandOutput.contains(FAILED_EXCEPTION_MSG)
        } catch (e: Exception) {
            throw e
        }
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    private fun removeEscapeCharacter(comm: String): String {
        return comm.replace("$", "\\$")
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationComponents.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))
    }

    companion object {
        private const val TAG = "RootController"
        private const val DISABLE_COMPONENT_TEMPLATE = "pm disable %s/%s"
        private const val ENABLE_COMPONENT_TEMPLATE = "pm enable %s/%s"
        private const val FAILED_EXCEPTION_MSG = "java.lang.IllegalArgumentException"
    }
}
