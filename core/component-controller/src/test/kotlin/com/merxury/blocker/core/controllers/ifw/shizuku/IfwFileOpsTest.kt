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

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IfwFileOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun writeThenRead_roundTrips() {
        val f = tmp.newFolder("ifw").resolve("p.xml").absolutePath
        assertTrue(IfwFileOps.writeAtomic(f, "<rules/>"))
        assertEquals("<rules/>", IfwFileOps.read(f))
    }

    @Test
    fun read_missing_returnsNull() {
        assertEquals(null, IfwFileOps.read(tmp.root.resolve("nope.xml").absolutePath))
    }

    @Test
    fun exists_and_delete() {
        val f = tmp.root.resolve("p.xml").absolutePath
        IfwFileOps.writeAtomic(f, "<rules/>")
        assertTrue(IfwFileOps.exists(f))
        assertTrue(IfwFileOps.delete(f))
        assertFalse(IfwFileOps.exists(f))
    }

    @Test
    fun list_returnsFileNames() {
        val dir = tmp.newFolder("ifw")
        IfwFileOps.writeAtomic(dir.resolve("a.xml").absolutePath, "<rules/>")
        IfwFileOps.writeAtomic(dir.resolve("b.xml").absolutePath, "<rules/>")
        assertEquals(listOf("a.xml", "b.xml"), IfwFileOps.list(dir.absolutePath).sorted())
    }

    @Test
    fun writeAtomic_leavesNoTempFile() {
        val dir = tmp.newFolder("ifw")
        IfwFileOps.writeAtomic(dir.resolve("p.xml").absolutePath, "<rules/>")
        assertEquals(listOf("p.xml"), dir.list()!!.toList())
    }
}
