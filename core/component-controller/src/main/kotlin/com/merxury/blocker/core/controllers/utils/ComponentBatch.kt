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

package com.merxury.blocker.core.controllers.utils

import android.content.ComponentName
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.merxury.blocker.core.model.data.ComponentInfo

// Bound Binder payloads even for apps declaring unusually many components.
private const val COMPONENTS_PER_BATCH = 200

internal suspend fun controlComponentBatches(
    components: List<ComponentInfo>,
    applyBatch: suspend (List<ComponentInfo>) -> Boolean,
    onApplied: suspend (ComponentInfo) -> Unit,
): Int {
    var succeeded = 0
    components.distinctBy { it.packageName to it.name }.groupBy { it.packageName }.values.forEach { packageComponents ->
        packageComponents.chunked(COMPONENTS_PER_BATCH).forEach { batch ->
            if (applyBatch(batch)) {
                batch.forEach { onApplied(it) }
                succeeded += batch.size
            }
        }
    }
    return succeeded
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun IPackageManager.setComponentsEnabled(
    components: List<ComponentName>,
    state: Int,
    userId: Int,
    callingPackage: String,
) {
    val settings = components.map { PackageManager.ComponentEnabledSetting(it, state, 0) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        setComponentEnabledSettings(settings, userId, callingPackage)
    } else {
        setComponentEnabledSettings(settings, userId)
    }
}
