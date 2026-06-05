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

import android.system.Os
import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.ParcelableLong
import be.mygod.librootkotlinx.ParcelableString
import be.mygod.librootkotlinx.ParcelableStringList
import be.mygod.librootkotlinx.RootCommand
import be.mygod.librootkotlinx.RootCommandNoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
internal data class RootListFilesCommand(
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
internal data class RootReadFileCommand(
    private val path: String,
) : RootCommand<ParcelableString> {
    override suspend fun execute(): ParcelableString = withContext(Dispatchers.IO) {
        ParcelableString(File(path).readText(Charsets.UTF_8))
    }
}

@Parcelize
internal data class RootWriteFileCommand(
    private val path: String,
    private val content: String,
) : RootCommandNoResult {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        File(path).writeText(content, Charsets.UTF_8)
        null
    }
}

@Parcelize
internal data class RootDeleteFileCommand(
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
internal data class RootFileExistsCommand(
    private val path: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean = withContext(Dispatchers.IO) {
        ParcelableBoolean(File(path).exists())
    }
}

@Parcelize
internal data class RootFileSizeCommand(
    private val path: String,
) : RootCommand<ParcelableLong> {
    override suspend fun execute(): ParcelableLong = withContext(Dispatchers.IO) {
        val file = File(path)
        ParcelableLong(if (file.exists()) file.length() else 0L)
    }
}

@Parcelize
internal data class RootChmodCommand(
    private val path: String,
    private val permission: Int,
    private val recursively: Boolean,
) : RootCommandNoResult {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        val mode = permission.toString().toInt(8)
        fun chmod(file: File) {
            Os.chmod(file.absolutePath, mode)
            if (recursively && file.isDirectory) {
                file.listFiles()?.forEach(::chmod)
            }
        }
        chmod(File(path))
        null
    }
}
