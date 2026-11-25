// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.DeleteWakeRuleFile
import com.merxury.blocker.core.privileged.calls.RemoveWakeRuleTarget
import com.merxury.blocker.core.privileged.calls.ReplaceWakeRuleFile
import com.merxury.blocker.core.privileged.uid
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
 * Writes the wake feature's rule files through the privileged channel the user chose. It never
 * connects the channel, so it never shows a prompt: each call runs only when the channel is
 * ready and throws otherwise.
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

    override suspend fun matchedProbeActions(sinceMillis: Long, actions: Set<String>): Set<String> {
        val identity = (executor.checkedState() as? ChannelState.Ready)?.identity
            ?: throw WakeSourceUnavailableException(CHANNEL_UNAVAILABLE)
        val log = WakeReads(executor, identity.uid, generation = 0).text(WakeCommand.IFW_PROBE, argument = sinceMillis)
        return actions.filterTo(mutableSetOf()) { action ->
            log.lineSequence().any { "ifw_intent_matched" in it && ",$action," in it }
        }
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
