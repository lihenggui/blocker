package com.merxury.blocker.work

import android.support.test.InstrumentationRegistry
import android.support.v7.preference.PreferenceManager
import com.merxury.blocker.R
import org.junit.Test

class ScheduledWorkTest {

    @Test
    fun doWork() {
        val context = InstrumentationRegistry.getTargetContext()
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)
        val isAutoBlockOn = preferenceManager.getBoolean(context.getString(R.string.key_pref_auto_block), false)
        val isForceDozeOn = preferenceManager.getBoolean(context.getString(R.string.key_pref_force_doze), false)
        assert(isAutoBlockOn)
        assert(isForceDozeOn)
//        val blockedApplications = ApplicationUtil.getBlockedApplication(context)
//        blockedApplications.forEach {
//            ManagerUtils.forceStop(it)
//        }
//        DeviceUtil.forceDoze()
    }
}