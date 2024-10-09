/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.hosts.parser

import com.merxury.blocker.core.hosts.parser.model.HostEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class FileHostsParser(
    private val hostsFile: File,
    private val dispatcher: CoroutineDispatcher,
) : HostsParser {

    override suspend fun parse(): List<HostEntry> = coroutineScope {
        if (!hostsFile.exists()) {
            return@coroutineScope emptyList()
        }

        hostsFile.useLines { lines ->
            val deferredEntries = lines.map { line ->
                async(dispatcher) { parseHostEntry(line) }
            }.toList()

            deferredEntries.awaitAll().filterNotNull()
        }
    }

    private fun parseHostEntry(line: String): HostEntry? {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return null
        }
        val parts = trimmedLine.split("#", limit = 2)
        val mainPart = parts[0].trim()
        val comment = if (parts.size > 1) parts[1].trim() else null

        val tokens = mainPart.split(Regex("\\s+"))
        if (tokens.size < 2) {
            return null
        }
        val ipAddress = tokens[0]
        val hostNames = tokens.subList(1, tokens.size)
        return HostEntry(ipAddress, hostNames, comment)
    }
}
