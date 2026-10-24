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

import com.merxury.blocker.core.utils.RootAvailabilityChecker
import com.merxury.core.ifw.IfwAccessChecker
import javax.inject.Inject

internal class RootOrShizukuIfwAccessChecker @Inject constructor(
    private val rootChecker: RootAvailabilityChecker,
    private val privilegeProvider: ShizukuPrivilegeProvider,
) : IfwAccessChecker {
    override suspend fun isIfwWritable(): Boolean = rootChecker.isRootAvailable() || privilegeProvider.ifwCapableUid() != null
}
