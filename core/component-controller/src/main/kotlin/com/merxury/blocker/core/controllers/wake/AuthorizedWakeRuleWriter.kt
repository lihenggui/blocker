// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.merxury.blocker.core.root.RootCommandExecutor
import com.merxury.core.ifw.RemovePackageWakeRules
import com.merxury.core.ifw.WakeRuleWriter
import com.merxury.core.ifw.WriteWakeRules
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

internal class AuthorizedWakeRuleWriter @Inject constructor(
    private val reader: WakeSystemReader,
    private val root: RootCommandExecutor,
) : WakeRuleWriter {
    override suspend fun removePackage(packageName: String) {
        when (val access = reader.access()) {
            WakeAccess.DIRECT_ROOT -> root.execute(RemovePackageWakeRules(packageName))
            WakeAccess.SHIZUKU_ROOT -> reader.withSession(access) { it.removeFirewallPackage(packageName) }
            else -> error("Root authorization unavailable")
        }
    }

    override suspend fun write(xml: String?, probe: Boolean) {
        when (val access = reader.access()) {
            WakeAccess.DIRECT_ROOT -> root.execute(WriteWakeRules(xml, probe))
            WakeAccess.SHIZUKU_ROOT -> reader.withSession(access) { it.writeFirewall(xml, probe) }
            else -> error("Root authorization unavailable")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WakeControlModule {
    @Binds abstract fun wakeRuleWriter(writer: AuthorizedWakeRuleWriter): WakeRuleWriter
}
