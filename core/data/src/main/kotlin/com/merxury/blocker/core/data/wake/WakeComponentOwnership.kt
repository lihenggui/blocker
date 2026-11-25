// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.controllers.wake.WakeCallRefusedException
import com.merxury.blocker.core.controllers.wake.WakeComponentJournal
import com.merxury.blocker.core.controllers.wake.WakeJournalUnreadableException
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Commits the privileged rollback journal before reflecting each change in Room. */
class WakeComponentOwnership @Inject constructor(
    private val history: WakeHistoryDao,
    private val journal: WakeComponentJournal,
) {
    // A manual override must survive the same interrupted write as a pending system change.
    private fun WakeProtection.needsComponentJournal() = method == "PM" && (configured || pendingChange || disabledByUser)

    /**
     * Reads the journal, the first file the wake feature needs, to find whether the channel can
     * reach it before anything is changed.
     *
     * @throws WakeCallRefusedException when the channel cannot run the calls that reach files,
     * as Shizuku before 13 cannot, although it runs as root
     * @throws WakeJournalUnreadableException when there is a journal this identity may not read
     */
    suspend fun probeJournal() {
        journal.read()
    }

    /** Blocks already made by wake protection that the user can take over under the package lock. */
    internal suspend fun claimedBlocks(user: UserId, candidates: Set<ComponentId>, catalog: ReviewedWakePaths): List<ComponentId> {
        if (user != UserId.SYSTEM || candidates.isEmpty()) return emptyList()
        return history.readProtections()
            .filter { it.method == "PM" && it.configured }
            .mapNotNull { path ->
                candidates.firstOrNull { it.packageName == path.target && it.className == path.component }
                    ?.takeIf { catalog.sameInstallation(path) }
            }
    }

    suspend fun recover() {
        val local = history.readProtections().filter { it.needsComponentJournal() }.associateBy { it.pathId }
        val stored = journal.read()
        if (stored == null) {
            // Migrate an existing local journal before making further PackageManager changes.
            if (local.isNotEmpty()) journal.write(Json.encodeToString(local.values.toList()))
            return
        }
        val durable = Json.decodeFromString<List<WakeProtection>>(stored).associateBy { it.pathId }
        check(durable.values.all { it.needsComponentJournal() && it.previousState != null })
        val released = local.values.filter { it.pathId !in durable && (it.configured || it.pendingChange) }
            .map { it.copy(configured = false, pendingChange = false) }
        history.putProtections(released + durable.values.filter { it != local[it.pathId] })
    }

    suspend fun save(path: WakeProtection) {
        val owned = history.readProtections().filter { it.needsComponentJournal() && it.pathId != path.pathId }
        val next = if (path.needsComponentJournal()) owned + path else owned
        journal.write(Json.encodeToString(next))
        history.putProtections(listOf(path))
    }
}
