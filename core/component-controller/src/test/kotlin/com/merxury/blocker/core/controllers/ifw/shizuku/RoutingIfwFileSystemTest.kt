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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class RecordingFs(val tag: String) : IfwFileSystem {
    override suspend fun readRules(packageName: String) = tag
    override suspend fun writeRules(packageName: String, content: String) = Unit
    override suspend fun deleteRules(packageName: String) = true
    override suspend fun fileExists(packageName: String) = true
    override suspend fun listRuleFiles() = listOf(tag)
}

class RoutingIfwFileSystemTest {
    private val root = RecordingFs("root")
    private val shizuku = RecordingFs("shizuku")
    private fun rootChecker(a: Boolean) = object : RootAvailabilityChecker {
        override suspend fun isRootAvailable() = a
    }
    private fun privilege(uid: Int?) = object : ShizukuPrivilegeProvider {
        override suspend fun ifwCapableUid() = uid
    }

    @Test
    fun prefersRoot_whenRootAvailable() = runTest {
        val fs = RoutingIfwFileSystem(root, shizuku, rootChecker(true), privilege(1000))
        assertEquals("root", fs.readRules("p"))
    }

    @Test
    fun fallsBackToShizuku_whenNoRoot() = runTest {
        val fs = RoutingIfwFileSystem(root, shizuku, rootChecker(false), privilege(1000))
        assertEquals("shizuku", fs.readRules("p"))
    }

    @Test
    fun throws_whenNeitherAvailable() = runTest {
        val fs = RoutingIfwFileSystem(root, shizuku, rootChecker(false), privilege(null))
        assertFailsWith<IfwUnavailableException> { fs.readRules("p") }
    }
}
