/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.domain.wake

import android.content.pm.PackageManager
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.privileged.wake.ManagedComponentState

/** PackageManager settings per component and user, changed by compare-and-set as the system does. */
internal class FakeComponentStates : ManagedComponentState {
    private val settings = mutableMapOf<Triple<String, String, UserId>, Int>()

    /** Each change that got past `proceed`, as `package/class user expected->new`. */
    val changes = mutableListOf<String>()

    fun setting(packageName: String, className: String, user: UserId = UserId.SYSTEM): Int = settings[Triple(packageName, className, user)] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    /** Sets a component as the user or the app would, outside the wake feature. */
    fun put(packageName: String, className: String, setting: Int, user: UserId = UserId.SYSTEM) {
        settings[Triple(packageName, className, user)] = setting
    }

    var beforeTakingLock: suspend () -> Unit = {}

    override suspend fun compareAndSet(
        packageName: String,
        className: String,
        user: UserId,
        expected: Int,
        newState: Int,
        afterConfirmed: suspend () -> Unit,
        proceed: suspend () -> Boolean,
    ): Boolean {
        beforeTakingLock()
        if (!proceed()) return false
        changes += "$packageName/$className ${user.value} $expected->$newState"
        val current = setting(packageName, className, user)
        if (current != expected && current != newState) return false
        if (current == expected) put(packageName, className, newState, user)
        afterConfirmed()
        return true
    }
}
