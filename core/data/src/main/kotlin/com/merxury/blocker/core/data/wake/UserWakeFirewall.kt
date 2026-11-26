// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.database.wake.WakeBlockDao
import com.merxury.blocker.core.database.wake.WakeBlockEntity
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.core.ifw.UserWakeRule
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeRuleWriter
import com.merxury.core.ifw.renderUserWakeRules
import timber.log.Timber
import javax.inject.Inject

/** Maintains the user firewall file under the caller's [WakeRuleLock]. */
class UserWakeFirewall @Inject constructor(
    private val dao: WakeBlockDao,
    private val writer: WakeRuleWriter,
    private val packages: SystemPackageReader,
) {
    internal suspend fun update(
        current: Collection<WakeBlockEntity>,
        added: List<WakeBlockEntity>,
        removed: List<String>,
    ) {
        val replaced = removed.toSet() + added.map { it.pathKey }
        val desired = current.filterNot { it.pathKey in replaced } + added
        write(desired)
        dao.update(added, removed)
    }

    internal suspend fun sync() {
        val current = dao.all()
        val absent = current.map { it.target }.distinct().filter { target ->
            when (val result = packages.isInstalled(AppKey(target, UserId.SYSTEM))) {
                is ReadResult.Success -> !result.value

                is ReadResult.Failure -> {
                    Timber.w("Cannot verify wake block target %s: %s", target, result.reason)
                    false
                }
            }
        }.toSet()
        val removed = current.filter { it.target in absent || (it.isFirewallBlock && !it.isServiceRule) }
        update(current, added = emptyList(), removed = removed.map { it.pathKey })
    }

    private suspend fun write(rows: Collection<WakeBlockEntity>) {
        val rules = rows.filter { it.isFirewallBlock && it.isServiceRule }
            .map { UserWakeRule(requireNotNull(it.sender), it.target, requireNotNull(it.component)) }
        writer.write(WakeRuleFileKind.USER, renderUserWakeRules(rules))
    }

    internal suspend fun forgetFirewallBlocks(packageName: String?) = dao.deleteFirewall(packageName)
}

internal val WakeBlockEntity.isFirewallBlock: Boolean get() = method == WakeBlockMethod.FIREWALL_RULE.name

private val WakeBlockEntity.isServiceRule: Boolean get() = componentType == ComponentType.SERVICE.name && component != null && sender != null
