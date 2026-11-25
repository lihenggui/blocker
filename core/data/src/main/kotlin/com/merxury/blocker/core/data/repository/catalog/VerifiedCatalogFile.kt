// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest

internal inline fun <T> CatalogSource.readVerified(
    file: CatalogFile,
    read: (InputStream) -> T,
): T = inCatalogFile(file.path) {
    VerifiedCatalogFile(open(file.path), file).use { stream ->
        val value = read(stream)
        stream.verify()
        value
    }
}

internal class VerifiedCatalogFile(input: InputStream, private val file: CatalogFile) : FilterInputStream(input) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var bytesRead = 0L
    private var lastByte = -1

    override fun read(): Int = super.read().also { byte ->
        if (byte >= 0) {
            count(1, byte)
            digest.update(byte.toByte())
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { read ->
        if (read > 0) {
            count(read, buffer[offset + read - 1].toInt())
            digest.update(buffer, offset, read)
        }
    }

    private fun count(count: Int, lastByte: Int) {
        verifyCatalog(file.size - bytesRead >= count) { "Size exceeds expected ${file.size} bytes" }
        bytesRead += count
        this.lastByte = lastByte
    }

    fun verify() {
        verifyCatalog(bytesRead == file.size) { "Size mismatch: expected ${file.size} bytes, read $bytesRead" }
        verifyCatalog(digest.digest().toHexString() == file.sha256) { "SHA-256 mismatch" }
        if (file.path == CATALOG_RULES || (file.path == CATALOG_COMPONENTS && bytesRead > 0)) {
            verifyCatalog(lastByte == '\n'.code) { "Expected a final line feed" }
        }
    }
}
