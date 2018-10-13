package com.merxury.libkit.utils

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
}