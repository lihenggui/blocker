// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.content.Context
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.DeleteWakeRuleFile
import com.merxury.blocker.core.privileged.calls.RemoveWakeRuleTarget
import com.merxury.blocker.core.privileged.calls.ReplaceWakeRuleFile
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeRuleWriter
import com.merxury.core.ifw.isWakePackageName
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Replaces or deletes this installation's managed service rules and temporary sender-filter probe
 * in `/data/system/ifw/`. Reconciliation applies or revokes managed rules and runs the probe when
 * needed. A reset removes one package's managed rules or deletes both files.
 *
 * The caller must hold the same mutex across reconciliation and resets, including the
 * probe and its cleanup. The writer relies on that mutex for serialization. It uses the
 * selected privileged channel only while it is ready and throws if the operation cannot run.
 */
internal class AuthorizedWakeRuleWriter @Inject constructor(
    private val executor: PrivilegedExecutor,
    @param:ApplicationContext context: Context,
) : WakeRuleWriter {
    private val owner = context.packageName

    override suspend fun write(kind: WakeRuleFileKind, xml: String?) {
        val call = if (xml == null) DeleteWakeRuleFile(owner, kind) else ReplaceWakeRuleFile(owner, kind, xml)
        executor.executeIfReady(call).orThrow("write the wake $kind rules")
    }

    override suspend fun reset(packageName: String?) {
        if (packageName == null) {
            WakeRuleFileKind.entries.forEach { write(it, null) }
        } else if (isWakePackageName(packageName)) {
            executor.executeIfReady(RemoveWakeRuleTarget(owner, WakeRuleFileKind.MANAGED, packageName))
                .orThrow("remove the wake rules of $packageName")
        }
        // Any other name cannot be in a wake rule, which names packages only.
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WakeControlModule {
    @Binds abstract fun wakeRuleWriter(writer: AuthorizedWakeRuleWriter): WakeRuleWriter

    @Binds abstract fun managedComponentState(state: ChannelManagedComponentState): ManagedComponentState
}
