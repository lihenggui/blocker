// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import com.merxury.blocker.core.model.wake.WakeAccess
import com.merxury.blocker.core.root.RootCommandExecutor
import com.merxury.core.ifw.ResetWakeRules
import com.merxury.core.ifw.WakeResetTransport
import com.merxury.core.ifw.WakeRuleWriter
import com.merxury.core.ifw.WriteWakeRules
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

internal class AuthorizedWakeRuleWriter @Inject constructor(
    private val reader: WakeSystemReader,
    private val root: RootCommandExecutor,
    @param:ApplicationContext private val context: Context,
) : WakeRuleWriter {
    override suspend fun reset(packageName: String?, transport: WakeResetTransport): Boolean = when (transport) {
        WakeResetTransport.DIRECT_ROOT -> root.execute(ResetWakeRules(context.packageName, packageName)).value
        WakeResetTransport.SHIZUKU_ROOT -> reader.withSession(WakeAccess.SHIZUKU_ROOT) { it.resetFirewall(packageName) }
    }

    override suspend fun write(xml: String?, probe: Boolean) {
        when (val access = reader.access()) {
            WakeAccess.DIRECT_ROOT -> root.execute(WriteWakeRules(context.packageName, xml, probe))
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
