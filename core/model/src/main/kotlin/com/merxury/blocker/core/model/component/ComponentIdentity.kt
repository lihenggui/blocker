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

package com.merxury.blocker.core.model.component

/**
 * An Android user on the device: the owner, a secondary user, or a profile such as a work profile.
 *
 * Only real users are represented. Android's pseudo ids for "all users" and "current user" are
 * negative and are not valid here.
 */
@JvmInline
value class UserId(val value: Int) {
    init {
        require(value >= 0) { "Not a real user id: $value" }
    }

    companion object {
        /** The first user on every device, which also owns device-wide settings. */
        val SYSTEM = UserId(0)
    }
}

/**
 * A component as the app declares it, the same for every user on the device.
 *
 * The APK is shared between users, so manifest facts are attached to this id. Intent Firewall
 * rules also name components this way and apply to all users at once.
 *
 * @property className the fully qualified class name; relative names are resolved by [unflatten]
 */
data class ComponentId(
    val packageName: String,
    val className: String,
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
        require(className.isNotBlank()) { "Class name is blank in $packageName" }
        require(!className.startsWith('.')) { "Class name is relative: $packageName/$className" }
        require('/' !in packageName && '/' !in className) {
            "Name contains a separator: $packageName/$className"
        }
    }

    /** The class name without its package, for display. */
    val simpleName: String get() = className.substringAfterLast('.')

    /** The `package/fully.qualified.Class` form used in Intent Firewall rules. */
    fun flatten(): String = "$packageName/$className"

    companion object {
        /**
         * Parses `package/class`, resolving a class that starts with `.` against the package as
         * Android does. Returns null when [flattened] is not in that form.
         */
        fun unflatten(flattened: String): ComponentId? {
            val separator = flattened.indexOf('/')
            if (separator <= 0 || separator == flattened.lastIndex) return null
            val packageName = flattened.substring(0, separator)
            val rawClassName = flattened.substring(separator + 1)
            val className = if (rawClassName.startsWith('.')) packageName + rawClassName else rawClassName
            if (packageName.isBlank() || className.isBlank() || '/' in className || className.startsWith('.')) {
                return null
            }
            return ComponentId(packageName, className)
        }
    }
}

/**
 * A component for one user, which is what a PackageManager setting belongs to.
 *
 * Each user keeps separate enabled settings for the same [ComponentId].
 */
data class ComponentKey(
    val id: ComponentId,
    val userId: UserId,
)

/**
 * An app for one user. The same package installed for the owner and in a work profile is two apps.
 */
data class AppKey(
    val packageName: String,
    val userId: UserId,
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
    }
}
