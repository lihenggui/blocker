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

package com.merxury.blocker.core.ui.applist

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ServiceCounts
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.ui.coil.ApkIcon

/** Rows for previews and screenshot tests. */
fun previewAppRows(user: UserId = UserId.SYSTEM): List<AppRow> {
    val services = ServiceCounts(running = 1, blocked = 2, total = 3)
    return listOf(
        previewRow(user, "com.merxury.blocker", "Blocker", "1.0.0", 1, isDisabled = true, isRunning = true, services),
        previewRow(user, "com.test.blocker", "Blocker Test", "11.0.0(1.1)", 11, isDisabled = true, isRunning = false, null),
        previewRow(user, "com.test", "Blocker Test test long long long long name", "0.1.1", 11, isDisabled = false, isRunning = true, services),
    )
}

private fun previewRow(
    user: UserId,
    packageName: String,
    label: String,
    versionName: String,
    versionCode: Long,
    isDisabled: Boolean,
    isRunning: Boolean,
    services: ServiceCounts?,
) = AppRow(
    key = AppKey(packageName, user),
    label = label,
    versionName = versionName,
    versionCode = versionCode,
    icon = ApkIcon(packageName, apkPath = null, versionCode = versionCode, uid = null),
    isDisabled = isDisabled,
    isRunning = isRunning,
    services = services,
)
