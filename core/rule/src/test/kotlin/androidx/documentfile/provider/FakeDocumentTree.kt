// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package androidx.documentfile.provider

import android.net.Uri

// DocumentFile's constructor is package-private.
internal class FakeDocumentTree(
    private val documentName: String,
    private val documentUri: Uri,
    private val directory: Boolean = true,
    parent: FakeDocumentTree? = null,
) : DocumentFile(parent) {
    private val entries = mutableListOf<FakeDocumentTree>()
    val lookups = mutableListOf<String>()
    val directoryCreations = mutableListOf<String>()
    val fileCreations = mutableListOf<Pair<String, String>>()

    fun addExternalFile(name: String): FakeDocumentTree = addEntry(name, directory = false)

    fun addExternalFolder(name: String): FakeDocumentTree = addEntry(name, directory = true)

    override fun findFile(displayName: String): FakeDocumentTree? {
        lookups += displayName
        return entries.firstOrNull { it.name == displayName }
    }

    override fun createFile(mimeType: String, displayName: String): FakeDocumentTree {
        fileCreations += mimeType to displayName
        val name = if (mimeType == "application/json") "$displayName.json" else displayName
        return addExternalFile(name)
    }

    override fun createDirectory(displayName: String): FakeDocumentTree {
        directoryCreations += displayName
        return addExternalFolder(displayName)
    }

    private fun addEntry(name: String, directory: Boolean): FakeDocumentTree {
        val entry = FakeDocumentTree(name, Uri.withAppendedPath(uri, name), directory, parent = this)
        entries += entry
        return entry
    }

    override fun listFiles(): Array<DocumentFile> = entries.toTypedArray()
    override fun getUri(): Uri = documentUri
    override fun getName(): String = documentName
    override fun getType(): String? = if (directory) null else "application/json"
    override fun isDirectory(): Boolean = directory
    override fun isFile(): Boolean = !directory
    override fun isVirtual(): Boolean = false
    override fun lastModified(): Long = 0
    override fun length(): Long = 0
    override fun canRead(): Boolean = true
    override fun canWrite(): Boolean = true
    override fun delete(): Boolean = error("Unexpected delete")
    override fun exists(): Boolean = true
    override fun renameTo(displayName: String): Boolean = error("Unexpected rename")
}
