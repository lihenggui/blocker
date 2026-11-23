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

package com.merxury.blocker.core.privileged

import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.calls.DeleteIfwRuleFile
import com.merxury.blocker.core.privileged.calls.ListIfwRuleFiles
import com.merxury.blocker.core.privileged.calls.ReadIfwRuleFile
import com.merxury.blocker.core.privileged.calls.ReplaceIfwRuleFile
import com.merxury.core.ifw.IfwAccessException
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.isPackageRuleFileName
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * The Intent Firewall rule files, reached through the privileged channel the user chose: the root
 * process, or Shizuku's user service when Shizuku runs as root or the system uid. Never falls
 * back from one to the other.
 *
 * Writes and [readRules] connect the channel when needed, which may show a permission prompt;
 * [readRulesIfConnected] never does. A channel that is unavailable, or whose identity may not
 * touch the files, throws [IfwAccessException]; any other failure throws [IOException].
 */
internal class ChannelIfwFileSystem @Inject constructor(
    private val executor: PrivilegedExecutor,
) : IfwFileSystem {

    override suspend fun readRules(packageName: String): String? = executor.execute(ReadIfwRuleFile(packageName)).getOrThrow("read the rules of $packageName")

    override suspend fun readRulesIfConnected(packageName: String): String? = executor.executeIfReady(ReadIfwRuleFile(packageName)).getOrThrow("read the rules of $packageName")

    override suspend fun writeRules(packageName: String, content: String) {
        executor.execute(ReplaceIfwRuleFile(packageName, content)).getOrThrow("write the rules of $packageName")
        Timber.i("Saved IFW rules of $packageName")
    }

    override suspend fun deleteRules(packageName: String): Boolean = executor.execute(DeleteIfwRuleFile(packageName)).getOrThrow("delete the rules of $packageName")
        .also { if (it) Timber.i("Deleted IFW rules of $packageName") }

    override suspend fun fileExists(packageName: String): Boolean = readRules(packageName) != null

    /** The packages that have a rule file, without the files the wake feature manages. */
    override suspend fun listRuleFiles(): List<String> = executor.execute(ListIfwRuleFiles).getOrThrow("list the rule files")
        .filter(::isPackageRuleFileName)

    private fun <R> CallResult<R>.getOrThrow(action: String): R = when (this) {
        is CallResult.Success -> value

        is CallResult.Failure -> when (val failure = reason) {
            CallFailure.ChannelUnavailable, is CallFailure.NotPermitted -> throw IfwAccessException("Cannot $action: $failure")
            is CallFailure.TargetNotFound, is CallFailure.SystemError -> throw IOException("Cannot $action: $failure")
        }
    }
}
