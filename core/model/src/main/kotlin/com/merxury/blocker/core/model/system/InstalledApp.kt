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

package com.merxury.blocker.core.model.system

import com.merxury.blocker.core.model.component.AppKey
import kotlin.time.Instant

/**
 * An app installed for one user.
 *
 * The APK, and so the version and SDK levels, is the same for every user that has the app. The
 * enabled setting and install times belong to the user in [key].
 *
 * @property uid the app's Linux uid in the user in [key], which differs between users, or null when
 * the system does not report it
 * @property enabled whether the app is enabled for the user in [key], as the system decides from
 * [enabledSetting] and, while that is [AppEnabledSetting.DEFAULT], the manifest's
 * `android:enabled`
 * @property enabledSetting the override set for the user in [key], which says why the app is
 * enabled or not
 * @property apkPath the base APK, or null when the system does not report one
 */
data class InstalledApp(
    val key: AppKey,
    val uid: Int?,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val enabled: Boolean,
    val enabledSetting: AppEnabledSetting,
    val apkPath: String?,
    val firstInstallTime: Instant,
    val lastUpdateTime: Instant,
    val minSdk: Int,
    val targetSdk: Int,
)

/**
 * The enabled setting PackageManager holds for a whole app and user. Unlike a component, an app
 * can be stored in any of the disabled states.
 */
enum class AppEnabledSetting {
    /** No override; the manifest's `android:enabled` on the application decides. */
    DEFAULT,
    ENABLED,
    DISABLED,

    /** Disabled on the user's behalf, which is what system settings and `pm disable-user` set. */
    DISABLED_USER,

    /** Disabled until the user next launches it. */
    DISABLED_UNTIL_USED,

    /** The setting could not be read. */
    UNKNOWN,
    ;

    /** Whether the setting is one of the disabled states. False for [UNKNOWN]. */
    val isDisabled: Boolean
        get() = this == DISABLED || this == DISABLED_USER || this == DISABLED_UNTIL_USED
}
