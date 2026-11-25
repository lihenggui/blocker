// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import com.merxury.blocker.core.model.CatalogState
import kotlinx.serialization.json.Json

internal const val CATALOG_MANIFEST = "catalog.json"
internal const val CATALOG_RULES = "rules.json"
internal const val CATALOG_COMPONENTS = "components.jsonl"

internal data class CatalogManifest(val state: CatalogState, val files: Map<String, CatalogFile>) {
    fun verifyPaths(paths: Set<String>) {
        val expected = files.keys + CATALOG_MANIFEST
        val extra = (paths - expected).firstOrNull()
        val missing = (expected - paths).firstOrNull()
        verifyCatalog(extra == null) { "Unknown file $extra" }
        verifyCatalog(missing == null) { "Missing file $missing" }
    }

    companion object {
        fun parse(bytes: ByteArray): CatalogManifest = inCatalogFile(CATALOG_MANIFEST) {
            verifyCatalog(bytes.lastOrNull() == '\n'.code.toByte()) { "Expected a final line feed" }
            val root = Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).catalogObject(CATALOG_MANIFEST)
            root.fields("format", "sequence", "sourceCommit", "publishedAt", "files")
            val format = root.nonNegativeInteger("format")
            verifyCatalog(format == 2L) { "Unsupported catalog format $format" }
            val publishedAt = root.string("publishedAt")
            verifyCatalog(publishedAt.endsWith("Z")) { "publishedAt must end in Z" }
            validateInstant(publishedAt, "publishedAt")
            val state = CatalogState(format.toInt(), root.nonNegativeInteger("sequence"), root.string("sourceCommit"), publishedAt)
            val files = root.array("files").map { entry ->
                val file = entry.catalogObject("files entry")
                file.fields("path", "size", "sha256")
                val path = file.string("path")
                val digest = file.string("sha256")
                verifyCatalog(path == CATALOG_RULES || path == CATALOG_COMPONENTS || isIconPath(path)) { "Unknown file $path" }
                verifyCatalog(SHA256.matches(digest)) { "Invalid SHA-256 for $path" }
                CatalogFile(path, file.nonNegativeInteger("size"), digest)
            }
            verifyCatalog(files.zipWithNext().all { (left, right) -> left.path.precedes(right.path) }) {
                "File paths must be sorted and unique"
            }
            val byPath = files.associateBy { it.path }
            for (path in listOf(CATALOG_RULES, CATALOG_COMPONENTS)) {
                verifyCatalog(path in byPath) { "Missing file $path from manifest" }
            }
            CatalogManifest(state, byPath)
        }

        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}

internal data class CatalogFile(val path: String, val size: Long, val sha256: String)

internal fun isIconPath(path: String): Boolean = path.startsWith("icon/") &&
    '\\' !in path && '\u0000' !in path &&
    path.split('/').none { it.isEmpty() || it == "." || it == ".." }
