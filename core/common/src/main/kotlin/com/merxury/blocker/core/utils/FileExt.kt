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

package com.merxury.blocker.core.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

internal fun File.isSymbolicLink(): Boolean {
    val canonicalParent = absoluteFile.parentFile?.canonicalFile ?: return false
    return File(canonicalParent, name).path != canonicalPath
}

/** Lists the files below this directory without descending into symbolic links. */
suspend fun File.listFilesRecursively(): List<File> {
    val context = currentCoroutineContext()
    val directories = ArrayDeque<File>()
    directories.add(this)
    return buildList {
        while (directories.isNotEmpty()) {
            context.ensureActive()
            val directory = directories.removeLast()
            val children = directory.listFiles() ?: throw IOException("Cannot list files in $directory")
            for (file in children) {
                context.ensureActive()
                if (file.isDirectory && !file.isSymbolicLink()) {
                    directories.add(file)
                } else {
                    add(file)
                }
            }
        }
    }
}
