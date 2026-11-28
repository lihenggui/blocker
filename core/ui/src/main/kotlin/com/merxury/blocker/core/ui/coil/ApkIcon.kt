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

package com.merxury.blocker.core.ui.coil

/**
 * The icon of an app installed for any user, as Coil data. The APK is the same for every user, so
 * the icon is read from [apkPath]; the app need not be installed for the user Blocker runs in.
 *
 * @property uid the app's uid in its user; an app in a work profile gets the system's work badge
 * from it. Null for no badge.
 */
data class ApkIcon(
    val packageName: String,
    val apkPath: String?,
    val versionCode: Long,
    val uid: Int?,
)
