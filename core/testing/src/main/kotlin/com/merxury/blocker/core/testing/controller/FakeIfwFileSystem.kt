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

import com.merxury.core.ifw.IfwFileSystem

class FakeIfwFileSystem : IfwFileSystem {
    private val files = mutableMapOf<String, String>()

    override suspend fun readRules(packageName: String): String? = files[packageName]

    override suspend fun writeRules(packageName: String, content: String) {
        files[packageName] = content
    }

    override suspend fun deleteRules(packageName: String): Boolean {
        return files.remove(packageName) != null
    }

    override suspend fun fileExists(packageName: String): Boolean =
        files.containsKey(packageName)
}
