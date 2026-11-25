// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.content.ComponentName
import com.merxury.blocker.core.control.PackageLocks
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledIfCurrent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Changes the PackageManager setting of a component the wake feature reviewed, keeping the cached
 * processes of its app.
 */
interface ManagedComponentState {
    /**
     * Holding [packageName]'s lock, the one the app's own component changes take, asks [proceed]
     * and, unless it says false, changes [className]'s setting for [user] from [expected] to
     * [newState]. Returns whether the setting holds [newState] afterwards; false without a change
     * when [proceed] said false or the setting held anything other than [expected] or [newState].
     * Calls [afterConfirmed] under the lock if the setting is confirmed. Throws when the change cannot be made.
     *
     * @param expected the setting the caller saw, `COMPONENT_ENABLED_STATE_DEFAULT`, `_ENABLED` or
     * `_DISABLED`
     * @param newState the setting to store, one of the same values
     */
    suspend fun compareAndSet(
        packageName: String,
        className: String,
        user: UserId,
        expected: Int,
        newState: Int,
        afterConfirmed: suspend () -> Unit = {},
        proceed: suspend () -> Boolean = { true },
    ): Boolean
}

/**
 * [ManagedComponentState] through the privileged channel the user chose, only when it is ready:
 * it never connects, so it never shows a prompt, and throws when the call cannot be made.
 */
internal class ChannelManagedComponentState @Inject constructor(
    private val executor: PrivilegedExecutor,
    private val locks: PackageLocks,
) : ManagedComponentState {
    override suspend fun compareAndSet(
        packageName: String,
        className: String,
        user: UserId,
        expected: Int,
        newState: Int,
        afterConfirmed: suspend () -> Unit,
        proceed: suspend () -> Boolean,
    ): Boolean = locks.withLock(packageName) {
        if (!proceed()) return@withLock false
        val call = SetComponentEnabledIfCurrent(ComponentName(packageName, className), user, expected, newState)
        withContext(NonCancellable) {
            executor.executeIfReady(call).orThrow("change $packageName/$className for user ${user.value}").also {
                if (it) afterConfirmed()
            }
        }
    }
}
