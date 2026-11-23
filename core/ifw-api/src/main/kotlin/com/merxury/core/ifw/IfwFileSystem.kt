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

package com.merxury.core.ifw

import java.io.IOException
import javax.inject.Qualifier

interface IfwFileSystem {
    /**
     * Returns the content of the package's rule file, or null if the file does not exist.
     * Throws if the file exists but cannot be read, so callers never mistake it for a missing file.
     * Throws [IfwAccessException] when the rule directory cannot be reached at all.
     */
    suspend fun readRules(packageName: String): String?

    /**
     * Like [readRules], but never acquires a privileged channel that is not connected yet, so it
     * never shows a permission prompt; throws [IfwAccessException] instead. For reads the user
     * did not ask for, such as the state shown in a list. An implementation that cannot tell the
     * two apart reads as [readRules] does.
     */
    suspend fun readRulesIfConnected(packageName: String): String? = readRules(packageName)

    /** Replaces the package's rule file atomically, so readers never see a partial file. */
    suspend fun writeRules(packageName: String, content: String)
    suspend fun deleteRules(packageName: String): Boolean
    suspend fun fileExists(packageName: String): Boolean

    /** The name, without `.xml`, of every rule file in the directory, as [isRuleFileName] decides. */
    suspend fun listRuleFiles(): List<String>
}

/**
 * Thrown by an [IfwFileSystem] when the Intent Firewall directory cannot be reached at all: no
 * privileged channel is available, or its identity may not touch files under `/data/system`.
 * Any other [IOException] means the directory was reached but this one operation failed.
 */
class IfwAccessException(message: String) : IOException(message)

/**
 * The [IfwFileSystem] that reaches the rule directory through the privileged channel the user
 * chose: the root process, or Shizuku running as root or the system uid. [IfwStore] uses it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PrivilegedIfwFileSystem
