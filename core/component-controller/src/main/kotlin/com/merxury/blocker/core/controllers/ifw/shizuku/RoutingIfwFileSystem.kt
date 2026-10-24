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
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.IfwUnavailableException
import com.merxury.core.ifw.RootIfwFs
import com.merxury.core.ifw.ShizukuIfwFs
import javax.inject.Inject

internal class RoutingIfwFileSystem @Inject constructor(
    @RootIfwFs private val rootFs: IfwFileSystem,
    @ShizukuIfwFs private val shizukuFs: IfwFileSystem,
    private val rootChecker: RootAvailabilityChecker,
    private val privilegeProvider: ShizukuPrivilegeProvider,
) : IfwFileSystem {

    private suspend fun delegate(): IfwFileSystem = when {
        rootChecker.isRootAvailable() -> rootFs
        privilegeProvider.ifwCapableUid() != null -> shizukuFs
        else -> throw IfwUnavailableException()
    }

    override suspend fun readRules(packageName: String): String? = delegate().readRules(packageName)

    override suspend fun writeRules(packageName: String, content: String) = delegate().writeRules(packageName, content)

    override suspend fun deleteRules(packageName: String): Boolean = delegate().deleteRules(packageName)

    override suspend fun fileExists(packageName: String): Boolean = delegate().fileExists(packageName)

    override suspend fun listRuleFiles(): List<String> = delegate().listRuleFiles()
}
