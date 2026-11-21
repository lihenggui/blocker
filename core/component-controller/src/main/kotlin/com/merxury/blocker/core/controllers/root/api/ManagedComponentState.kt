// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.root.api

import android.content.ComponentName
import android.content.Context
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.Build
import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.RootCommand
import com.merxury.blocker.core.controllers.wake.WakeAccess
import com.merxury.blocker.core.controllers.wake.WakeSystemReader
import com.merxury.blocker.core.root.RootCommandExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.parcelize.Parcelize
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import javax.inject.Inject

/** Preserve cached processes when applying a reviewed dedicated-component change. */
class ManagedComponentState @Inject constructor(
    private val executor: RootCommandExecutor,
    private val reader: WakeSystemReader,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun compareAndSet(pkg: String, name: String, before: Int, after: Int): Boolean {
        val change = ChangeIfCurrent(pkg, name, before, after)
        return when (reader.access()) {
            WakeAccess.DIRECT_ROOT -> executor.execute(change).value

            WakeAccess.SHIZUKU_ROOT, WakeAccess.SHIZUKU_LEGACY_ROOT -> {
                val pm = IPackageManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
                change.apply(pm, context.packageName)
            }

            else -> error("Root authorization unavailable")
        }
    }
}

@Parcelize
private data class ChangeIfCurrent(
    val pkg: String,
    val name: String,
    val before: Int,
    val after: Int,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute() = ParcelableBoolean(apply(RootApiServices.pm, RootApiServices.packageName))

    // The policy is restricted to devices with a single owner profile.
    fun apply(pm: IPackageManager, caller: String): Boolean {
        val component = ComponentName(pkg, name)
        val user = 0
        val current = pm.getComponentEnabledSetting(component, user)
        if (current == after) return true
        if (current != before) return false
        if (Build.VERSION.SDK_INT >= 34) {
            pm.setComponentEnabledSetting(component, after, PackageManager.DONT_KILL_APP, user, caller)
        } else {
            pm.setComponentEnabledSetting(component, after, PackageManager.DONT_KILL_APP, user)
        }
        return pm.getComponentEnabledSetting(component, user) == after
    }
}
