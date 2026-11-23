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

package com.merxury.blocker.core.privileged.calls

import android.content.ComponentName
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import com.merxury.blocker.core.controllers.utils.setComponentsEnabled
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.privileged.PrivilegedAction
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * The PackageManager setting of each of [classNames], components of [packageName], for [user],
 * in one call.
 *
 * The result has one entry per class name, in order: `COMPONENT_ENABLED_STATE_DEFAULT`,
 * `_ENABLED` or `_DISABLED`, or null when that setting cannot be read, because the system rejects
 * the name or holds a value a component cannot have. One unreadable name does not fail the rest.
 *
 * @throws NameNotFoundException when [packageName] is not installed for [user]
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ComponentEnabledSettings(
    val packageName: String,
    val classNames: List<String>,
    val user: UserId,
) : PrivilegedCall<List<Int?>> {
    override fun run(system: SystemServices): List<Int?> {
        packageInfo(system, packageName, 0, user)
            ?: throw NameNotFoundException("$packageName is not installed for user ${user.value}")
        return classNames.map { className ->
            try {
                system.packageManager.getComponentEnabledSetting(ComponentName(packageName, className), user.value)
                    .takeIf { it in COMPONENT_SETTINGS }
            } catch (_: IllegalArgumentException) {
                // Android 9 and 14 answer DEFAULT for an undeclared name; this covers a release that refuses.
                null
            }
        }
    }
}

/**
 * Sets [components] to [newState] for [user]. Their apps are stopped, as the system does by
 * default. From Android 13 this is one system call, which the system applies to all of them or,
 * when it rejects one, to none; before that it is one system call per component, so a failure
 * part way leaves the earlier ones changed. Only root may change components.
 *
 * @param newState `COMPONENT_ENABLED_STATE_DEFAULT`, `_ENABLED` or `_DISABLED`
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class SetComponentEnabledSettings(
    val components: List<ComponentName>,
    val newState: Int,
    val user: UserId,
) : PrivilegedAction {
    init {
        require(newState in COMPONENT_SETTINGS) { "Not a component enabled setting: $newState" }
    }

    override fun run(system: SystemServices) {
        if (components.isEmpty()) return
        val pm = system.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.setComponentsEnabled(components, newState, user.value, system.callerPackage)
        } else {
            components.forEach { pm.setComponentEnabledSetting(it, newState, 0, user.value) }
        }
    }
}

/**
 * Changes [component]'s PackageManager setting for [user] from [expected] to [newState] without
 * stopping its app (`DONT_KILL_APP`), only if it still holds [expected]. True when the setting
 * already is [newState], or reads back as [newState] after the change; false, without a change,
 * when it holds anything else. Only root and the system uid may change components.
 *
 * @param expected the setting the caller saw, `COMPONENT_ENABLED_STATE_DEFAULT`, `_ENABLED` or
 * `_DISABLED`
 * @param newState the setting to store, one of the same values
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class SetComponentEnabledIfCurrent(
    val component: ComponentName,
    val user: UserId,
    val expected: Int,
    val newState: Int,
) : PrivilegedCall<Boolean> {
    init {
        require(expected in COMPONENT_SETTINGS) { "Not a component enabled setting: $expected" }
        require(newState in COMPONENT_SETTINGS) { "Not a component enabled setting: $newState" }
    }

    override fun run(system: SystemServices): Boolean = system.packageManager.compareAndSetComponentEnabled(component, user, expected, newState, system.callerPackage)
}

/** The compare-and-set of [SetComponentEnabledIfCurrent]; see there. */
internal fun IPackageManager.compareAndSetComponentEnabled(
    component: ComponentName,
    user: UserId,
    expected: Int,
    newState: Int,
    callerPackage: String,
): Boolean {
    val current = getComponentEnabledSetting(component, user.value)
    if (current == newState) return true
    if (current != expected) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP, user.value, callerPackage)
    } else {
        setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP, user.value)
    }
    return getComponentEnabledSetting(component, user.value) == newState
}

// The only values PackageManager stores for a component.
private val COMPONENT_SETTINGS = setOf(
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
)
