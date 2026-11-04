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

package com.merxury.blocker.core.controllers.ifw.shizuku

import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import javax.inject.Inject

private const val ROOT_UID = 0
private const val SYSTEM_UID = 1000

internal class RealShizukuPrivilegeProvider @Inject constructor(
    private val shizukuInitializer: IShizukuInitializer,
) : ShizukuPrivilegeProvider {
    override suspend fun ifwCapableUid(): Int? {
        if (!shizukuInitializer.hasPermission()) return null
        return when (shizukuInitializer.getUid()) {
            ROOT_UID -> ROOT_UID
            SYSTEM_UID -> SYSTEM_UID
            else -> null
        }
    }
}
