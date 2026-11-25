// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext

internal fun unpackCatalogIcons(archive: File, payload: File, manifest: CatalogManifest, context: CoroutineContext) {
    val listed = RandomAccessFile(archive, "r").use { it.readIconDirectory(manifest, context) }
    val icons = payload.resolve("icon")
    if (listed.isNotEmpty() && !icons.mkdir()) throw IOException("Cannot create staged icon directory")

    val extracted = mutableSetOf<String>()
    ZipInputStream(archive.inputStream().buffered()).use { zip ->
        while (true) {
            context.ensureActive()
            val entry = try {
                zip.nextEntry
            } catch (e: IllegalArgumentException) {
                throw InvalidCatalog("Invalid ZIP entry name", e)
            } ?: break
            val path = entry.name
            verifyCatalog(!entry.isDirectory && path in listed) { "Unexpected ZIP entry $path" }
            verifyCatalog(extracted.add(path)) { "Duplicate ZIP entry $path" }
            val file = manifest.files.getValue(path)
            payload.resolve(path).outputStream().buffered().use { output ->
                zip.copyCatalogBytes(output, file.size, path, context)
            }
            zip.closeEntry()
        }
    }
    verifyCatalog(extracted == listed) { "ZIP local entries do not match the central directory" }
    // Missing manifest files, exact sizes and hashes are checked by the installer.
}

/** ZipEntry exposes directory names but hides Unix file types, so read those from the central directory. */
private fun RandomAccessFile.readIconDirectory(manifest: CatalogManifest, context: CoroutineContext): Set<String> {
    val end = findDirectoryEnd()
    val count = end.unsignedShort(10)
    verifyCatalog(
        end.unsignedShort(4) == 0 && end.unsignedShort(6) == 0 &&
            end.unsignedShort(8) == count && count != 0xffff,
    ) { "Unsupported ZIP disk or ZIP64 layout" }
    val offset = end.unsignedInt(16)
    val limit = offset + end.unsignedInt(12)
    verifyCatalog(limit == filePointer) { "Invalid ZIP central directory bounds" }
    seek(offset)

    val paths = mutableSetOf<String>()
    val bytes = ByteArray(46)
    val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    repeat(count) {
        context.ensureActive()
        verifyCatalog(limit - filePointer >= bytes.size) { "Truncated ZIP central directory" }
        readFully(bytes)
        verifyCatalog(header.getInt(0) == 0x02014b50) { "Invalid ZIP central directory entry" }

        val nameLength = header.unsignedShort(28)
        val next = filePointer + nameLength + header.unsignedShort(30) + header.unsignedShort(32)
        verifyCatalog(next <= limit && header.unsignedShort(34) == 0) { "Invalid ZIP entry bounds" }
        val path = inCatalogFile("ZIP entry name") {
            ByteArray(nameLength).also { readFully(it) }.decodeToString(throwOnInvalidSequence = true)
        }
        val attributes = header.getInt(38)
        val fileType = attributes ushr 16 and 0xf000
        verifyCatalog(attributes and 0x18 == 0 && (fileType == 0 || fileType == 0x8000)) { "Non-regular ZIP entry $path" }
        verifyCatalog(isIconPath(path) && path.count { it == '/' } == 1 && path in manifest.files) {
            "Unexpected ZIP entry $path"
        }
        verifyCatalog(paths.add(path)) { "Duplicate ZIP entry $path" }
        seek(next)
    }
    verifyCatalog(filePointer == limit) { "Unexpected ZIP central directory data" }
    return paths
}

private fun RandomAccessFile.findDirectoryEnd(): ByteBuffer {
    val tail = ByteArray(minOf(length(), 22L + 0xffff).toInt())
    val start = length() - tail.size
    seek(start)
    readFully(tail)
    val bytes = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
    for (index in tail.size - 22 downTo 0) {
        if (bytes.getInt(index) == 0x06054b50 && index + 22 + bytes.unsignedShort(index + 20) == tail.size) {
            seek(start + index)
            return ByteBuffer.wrap(tail, index, 22).slice().order(ByteOrder.LITTLE_ENDIAN)
        }
    }
    throw InvalidCatalog("Missing ZIP central directory")
}

private fun ByteBuffer.unsignedShort(index: Int): Int = getShort(index).toInt() and 0xffff
private fun ByteBuffer.unsignedInt(index: Int): Long = getInt(index).toLong() and 0xffffffffL
