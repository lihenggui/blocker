// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import java.io.IOException

interface FirewallResetRepository {
    /**
     * Resets the rules of the package [target], or every rule file when null, reporting each
     * file with [onDelete] (rule file name, position from 1, count) before deleting it.
     *
     * Returns the number of rule files removed, or null when the rule directory cannot be reached
     * through the chosen channel. Throws [IOException] when deleting a file fails; files deleted
     * before it stay deleted. The wake feature's files are reset in every case, when the channel
     * may write them.
     */
    suspend fun reset(target: String?, onDelete: suspend (String, Int, Int) -> Unit): Int?
}
