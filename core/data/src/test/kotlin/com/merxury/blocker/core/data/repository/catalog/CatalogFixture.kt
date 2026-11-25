// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal const val FIXTURE_ICON = "icon/nested/example.svg"
internal const val FIXTURE_COMPONENT_NAME = "com.example.Component"
internal const val FIXTURE_COMPONENT = """{"name":"$FIXTURE_COMPONENT_NAME","sdkRuleId":1,"recommendToBlock":false,"i18n":{"en":{"description":""}}}"""
internal const val FIXTURE_RULES = """{"rules":[{"id":1,"icon":"$FIXTURE_ICON","keywords":["second","first"],"contributors":[],"i18n":{"en":{"name":"Example"},"zh-CN":{"description":"Localized"}}}]}"""

internal class CatalogFixture(
    val files: MutableMap<String, ByteArray>,
) : CatalogSource {
    val opens = ConcurrentHashMap<String, Int>()
    var openOverride: (String, ByteArray) -> InputStream = { _, bytes -> ByteArrayInputStream(bytes) }

    override fun paths(): Set<String> = files.keys

    override fun open(path: String): InputStream {
        opens.merge(path, 1, Int::plus)
        return openOverride(path, files[path] ?: throw FileNotFoundException(path))
    }
}

internal fun fixtureCatalog(
    sequence: Long,
    format: Int = 2,
    rules: String = FIXTURE_RULES,
    components: String = "$FIXTURE_COMPONENT\n",
    icon: String = "old icon",
    sourceCommit: String = "fixture",
): CatalogFixture {
    val files = mutableMapOf(
        CATALOG_RULES to "$rules\n".encodeToByteArray(),
        CATALOG_COMPONENTS to components.encodeToByteArray(),
        FIXTURE_ICON to icon.encodeToByteArray(),
    )
    val manifest = buildJsonObject {
        put("format", format)
        put("sequence", sequence)
        put("sourceCommit", sourceCommit)
        put("publishedAt", "2026-11-25T00:00:00Z")
        putJsonArray("files") {
            files.toSortedMap().forEach { (path, bytes) ->
                addJsonObject {
                    put("path", path)
                    put("size", bytes.size)
                    put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
                }
            }
        }
    }
    files[CATALOG_MANIFEST] = (Json.encodeToString(manifest) + "\n").encodeToByteArray()
    return CatalogFixture(files)
}
