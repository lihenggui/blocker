// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged

import android.app.AppOpsManager.MODE_ALLOWED
import android.os.IBinder
import android.os.IDeviceIdleController
import com.android.internal.app.IAppOpsService

internal class FakeAppOpsService(private val changes: MutableList<String>) : IAppOpsService {
    val modes = mutableMapOf<Triple<Int, Int, String>, Int>()
    var writeFailure: Exception? = null
    var ignoredWriteCode: Int? = null

    override fun checkOperation(code: Int, uid: Int, packageName: String): Int = modes[Triple(code, uid, packageName)] ?: MODE_ALLOWED

    override fun setMode(code: Int, uid: Int, packageName: String, mode: Int) {
        writeFailure?.let { throw it }
        changes += "mode $code $uid $packageName $mode"
        if (code != ignoredWriteCode) modes[Triple(code, uid, packageName)] = mode
    }

    override fun asBinder(): IBinder = error("No binder")
}

internal class FakeDeviceIdleController(private val changes: MutableList<String>) : IDeviceIdleController {
    val systemAllowlist = mutableSetOf<String>()
    val userAllowlist = mutableSetOf<String>()
    var removeFailure: Exception? = null

    override fun getSystemPowerWhitelist(): Array<String> = systemAllowlist.toTypedArray()

    override fun getUserPowerWhitelist(): Array<String> = userAllowlist.toTypedArray()

    override fun removePowerSaveWhitelistApp(packageName: String) {
        removeFailure?.let { throw it }
        changes += "remove $packageName"
        userAllowlist -= packageName
    }

    override fun asBinder(): IBinder = error("No binder")
}
