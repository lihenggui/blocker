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

package com.merxury.blocker.core.root

import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.ParcelableString
import be.mygod.librootkotlinx.ParcelableStringList
import be.mygod.librootkotlinx.RootCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class RootListFilesCommand(
    private val path: String,
) : RootCommand<ParcelableStringList> {
    override suspend fun execute(): ParcelableStringList = withContext(Dispatchers.IO) {
        val file = File(path)
        ParcelableStringList(
            if (file.exists()) {
                file.list()?.toList() ?: emptyList()
            } else {
                emptyList()
            },
        )
    }
}

@Parcelize
data class RootReadFileCommand(
    private val path: String,
) : RootCommand<ParcelableString> {
    override suspend fun execute(): ParcelableString = withContext(Dispatchers.IO) {
        ParcelableString(File(path).readText(Charsets.UTF_8))
    }
}

@Parcelize
data class RootDeleteFileCommand(
    private val path: String,
    private val recursively: Boolean,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean = withContext(Dispatchers.IO) {
        val file = File(path)
        ParcelableBoolean(
            when {
                !file.exists() -> false
                recursively -> file.deleteRecursively()
                else -> file.delete()
            },
        )
    }
}

@Parcelize
data class RootFileExistsCommand(
    private val path: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean = withContext(Dispatchers.IO) {
        ParcelableBoolean(File(path).exists())
    }
}
