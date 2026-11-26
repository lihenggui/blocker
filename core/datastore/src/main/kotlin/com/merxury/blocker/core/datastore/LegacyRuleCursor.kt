// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.datastore

import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat
import java.io.ByteArrayOutputStream

private const val LEGACY_RULE_CURSOR_FIELD = 17

// Protobuf retains reserved fields as unknown data. Strip only the old cursor so a downgrade
// reads an empty cursor and other preferences, including unknown fields, survive the migration.
internal fun UserPreferences.withoutLegacyRuleCursor(): UserPreferences {
    val bytes = toByteArray()
    val input = CodedInputStream.newInstance(bytes)
    val retained = ByteArrayOutputStream()
    var retainedStart = 0
    while (!input.isAtEnd) {
        val fieldStart = input.totalBytesRead
        val tag = input.readTag()
        input.skipField(tag)
        if (WireFormat.getTagFieldNumber(tag) == LEGACY_RULE_CURSOR_FIELD) {
            retained.write(bytes, retainedStart, fieldStart - retainedStart)
            retainedStart = input.totalBytesRead
        }
    }
    if (retainedStart == 0) return this
    retained.write(bytes, retainedStart, bytes.size - retainedStart)
    return UserPreferences.parseFrom(retained.toByteArray())
}
