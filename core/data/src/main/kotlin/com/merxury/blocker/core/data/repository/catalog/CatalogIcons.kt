// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import com.merxury.blocker.core.di.FilesDir
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

internal class CatalogIcons @Inject constructor(@FilesDir filesDir: File) {
    private val root = filesDir.resolve("catalog")

    fun directory(sequence: Long): File = root.resolve("icon-$sequence")

    fun prepare(directory: File) {
        // A process can stop before committing and leave this sequence's files on disk.
        if (!directory.deleteRecursively() || !directory.mkdirs()) {
            throw IOException("Cannot prepare catalog icon directory ${directory.name}")
        }
    }

    fun resolve(sequence: Long, path: String): File? {
        if (!isIconPath(path)) return null
        return directory(sequence).resolve(path).takeIf { it.isFile }
    }

    fun deleteOlderThan(sequence: Long) {
        val directories = root.listFiles()
        if (directories == null && root.exists()) Timber.w("Cannot list catalog icon directories")
        directories.orEmpty().forEach { file ->
            val suffix = file.name.removePrefix("icon-")
            if (suffix != file.name && suffix.toLongOrNull()?.let { it < sequence } == true) delete(file)
        }
    }

    fun delete(directory: File) {
        if (!directory.deleteRecursively()) Timber.w("Cannot delete catalog icon directory %s", directory.name)
    }
}
