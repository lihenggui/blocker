/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.testing.controller

import com.merxury.core.ifw.IfwAccessException
import com.merxury.core.ifw.IfwFileSystem
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Rule files in memory, with switches for the ways the real directory fails. Every operation is
 * recorded in [operations], and [beforeRead] runs before each read so tests can interleave callers.
 */
class FakeIfwFileSystem : IfwFileSystem {
    private val files = ConcurrentHashMap<String, String>()

    /** Packages whose rule file exists but cannot be read. [readRules] throws for them. */
    val unreadablePackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** When true, every operation throws [IfwAccessException], as without a usable channel. */
    @Volatile
    var noAccess = false

    /** When false, [readRulesIfConnected] throws [IfwAccessException], as before a connect. */
    @Volatile
    var connected = true

    /** Thrown by writes and deletes when set, which then leave the file as it was. */
    @Volatile
    var writeFailure: IOException? = null

    /** Runs before each read with the package name. */
    @Volatile
    var beforeRead: suspend (String) -> Unit = {}

    /** Each operation as `read`, `peek`, `write`, `delete` or `list`, followed by its package. */
    val operations: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** The writes and deletes so far. */
    val changeCount: Int get() = operations.count { it.startsWith("write") || it.startsWith("delete") }

    /** Sets the file's content as another tool would, past every switch. */
    fun putFile(packageName: String, content: String) {
        files[packageName] = content
    }

    fun fileContent(packageName: String): String? = files[packageName]

    override suspend fun readRules(packageName: String): String? {
        operations += "read $packageName"
        return read(packageName)
    }

    override suspend fun readRulesIfConnected(packageName: String): String? {
        operations += "peek $packageName"
        if (!connected) throw IfwAccessException("Not connected")
        return read(packageName)
    }

    private suspend fun read(packageName: String): String? {
        checkAccess()
        beforeRead(packageName)
        if (packageName in unreadablePackages) throw IOException("Cannot read IFW rules for $packageName")
        return files[packageName]
    }

    override suspend fun writeRules(packageName: String, content: String) {
        operations += "write $packageName"
        checkAccess()
        writeFailure?.let { throw it }
        files[packageName] = content
    }

    override suspend fun deleteRules(packageName: String): Boolean {
        operations += "delete $packageName"
        checkAccess()
        writeFailure?.let { throw it }
        // Deleting needs no read, so an unreadable file goes too.
        unreadablePackages.remove(packageName)
        return files.remove(packageName) != null
    }

    override suspend fun fileExists(packageName: String): Boolean = files.containsKey(packageName)

    override suspend fun listRuleFiles(): List<String> {
        operations += "list"
        checkAccess()
        return files.keys.sorted()
    }

    private fun checkAccess() {
        if (noAccess) throw IfwAccessException("No access")
    }
}
