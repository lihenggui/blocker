// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import kotlin.time.Instant

internal class InvalidCatalog(message: String, cause: Throwable? = null) : IOException(message, cause)

internal inline fun verifyCatalog(condition: Boolean, reason: () -> String) {
    if (!condition) throw InvalidCatalog(reason())
}

internal inline fun <T> inCatalogFile(path: String, block: () -> T): T = try {
    block()
} catch (e: SerializationException) {
    throw InvalidCatalog("Invalid JSON in $path", e)
} catch (e: IOException) {
    throw InvalidCatalog("$path: ${e.message}", e)
}

internal fun JsonElement?.catalogObject(field: String): JsonObject = this as? JsonObject ?: throw InvalidCatalog("Expected an object for $field")

internal fun JsonObject.fields(vararg allowed: String) {
    val unknown = keys.firstOrNull { it !in allowed }
    verifyCatalog(unknown == null) { "Unknown field $unknown" }
}

internal fun JsonObject.array(field: String): JsonArray = this[field] as? JsonArray ?: throw InvalidCatalog("Expected an array for $field")

private fun JsonElement?.string(field: String): String = (this as? JsonPrimitive)
    ?.takeIf { it.isString }
    ?.content
    ?: throw InvalidCatalog("Expected a string for $field")

internal fun JsonObject.string(field: String): String = this[field].string(field)

internal fun JsonObject.optionalString(field: String): String? = this[field]?.string(field)

internal fun JsonObject.strings(field: String): List<String> = array(field).map { it.string(field) }

internal fun JsonObject.nonNegativeInteger(field: String): Long {
    val literal = (this[field] as? JsonPrimitive)?.takeUnless { it.isString }?.content
    return literal?.takeIf { NON_NEGATIVE_INTEGER.matches(it) }?.toLongOrNull()
        ?: throw InvalidCatalog("Expected a non-negative integer for $field")
}

internal fun JsonObject.positiveInteger(field: String): Long = nonNegativeInteger(field).also {
    verifyCatalog(it > 0) { "Expected a positive integer for $field" }
}

internal fun JsonObject.boolean(field: String): Boolean = when ((this[field] as? JsonPrimitive)?.takeUnless { it.isString }?.content) {
    "true" -> true
    "false" -> false
    else -> throw InvalidCatalog("Expected a boolean for $field")
}

internal fun validateInstant(value: String, field: String) {
    try {
        Instant.parse(value)
    } catch (e: IllegalArgumentException) {
        throw InvalidCatalog("Expected an ISO instant for $field", e)
    }
}

/** The publisher sorts names and paths by Unicode code point, including supplementary characters. */
internal fun String.precedes(next: String): Boolean {
    var offset = 0
    while (offset < length && offset < next.length) {
        val left = codePointAt(offset)
        val right = next.codePointAt(offset)
        if (left != right) return left < right
        offset += Character.charCount(left)
    }
    return length < next.length
}

private val NON_NEGATIVE_INTEGER = Regex("0|[1-9][0-9]*")
