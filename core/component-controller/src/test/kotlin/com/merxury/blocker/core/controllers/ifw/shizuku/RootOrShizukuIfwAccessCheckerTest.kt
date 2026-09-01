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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootOrShizukuIfwAccessCheckerTest {
    private fun rootChecker(available: Boolean) = object : RootAvailabilityChecker {
        override suspend fun isRootAvailable() = available
    }
    private fun privilege(uid: Int?) = object : ShizukuPrivilegeProvider {
        override suspend fun ifwCapableUid() = uid
    }

    @Test
    fun rootAvailable_isWritable() = runTest {
        val checker: IfwAccessChecker =
            RootOrShizukuIfwAccessChecker(rootChecker(true), privilege(null))
        assertTrue(checker.isIfwWritable())
    }

    @Test
    fun shizukuSystem_isWritable() = runTest {
        val checker: IfwAccessChecker =
            RootOrShizukuIfwAccessChecker(rootChecker(false), privilege(1000))
        assertTrue(checker.isIfwWritable())
    }

    @Test
    fun neither_isNotWritable() = runTest {
        val checker: IfwAccessChecker =
            RootOrShizukuIfwAccessChecker(rootChecker(false), privilege(null))
        assertFalse(checker.isIfwWritable())
    }
}
