package com.merxury.libkit.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import com.merxury.libkit.RootCommand


object DeviceUtil {
    fun enterLowPowerConsumptionMode() {
        RootCommand.runBlockingCommand("dumpsys battery unplug")
        RootCommand.runBlockingCommand("dumpsys deviceidle step")
    }

    fun forceDoze() {
        RootCommand.runBlockingCommand("dumpsys deviceidle force-idle")
    } 

    fun exitDoze() {
        RootCommand.runBlockingCommand("shell input keyevent KEYCODE_WAKEUP")
    }

    @Suppress("DEPRECATION")
    fun isScreenOn(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            var screenOn = false
            for (display in dm.displays) {
                if (display.state != Display.STATE_OFF) {
                    screenOn = true
                }
            }
            screenOn
        } else {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isScreenOn
        }
    }
}