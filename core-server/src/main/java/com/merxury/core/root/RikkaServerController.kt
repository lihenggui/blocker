package com.merxury.core.root

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import com.merxury.core.IController
import moe.shizuku.api.ShizukuClient
import moe.shizuku.api.ShizukuPackageManagerV26

class RikkaServerController constructor(context: Context) : IController {
    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        try {
            ShizukuPackageManagerV26.setComponentEnabledSetting(ComponentName(packageName, componentName), state, PackageManager.DONT_KILL_APP, Binder.getCallingUid())
        } catch (e: RuntimeException) {
            e.printStackTrace();
            Log.e(TAG, "Error occurs while sending request to RikkaServer")
            return false
        }
        return true
    }

    override fun enableComponent(componentInfo: ComponentInfo): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disableComponent(componentInfo: ComponentInfo): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    init {
        ShizukuClient.initialize(context)
    }

    companion object {
        const val TAG = "RikkaServerController"
        @Volatile
        var instance: RikkaServerController? = null

        fun getInstance(context: Context): RikkaServerController {
            if (instance == null) {
                synchronized(RikkaServerController::class) {
                    if (instance == null) {
                        instance = RikkaServerController(context)
                    }
                }
            }
            return instance!!
        }
    }
}