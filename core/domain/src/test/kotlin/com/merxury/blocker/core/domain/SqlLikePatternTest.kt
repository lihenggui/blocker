/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.domain

import android.database.sqlite.SQLiteDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], manifest = Config.NONE)
class SqlLikePatternTest {
    @Test
    fun matchesExistingSqlForLiteralWildcardCaseAndUnicodePatterns() {
        val random = Random(1565)
        val alphabet = listOf("a", "b", "A", "B", ".", "_", "%", "\\", "å", "Å", "😀", "\n")
        fun generated(maxLength: Int) = buildString {
            repeat(random.nextInt(maxLength + 1)) { append(alphabet.random(random)) }
        }
        val names = listOf(
            "com.google.firebase.firebase_ads.Service",
            "com.google.firebase.firebaseXads.Service",
            "com.GOOGLE.Firebase.Tracker",
            "com.example.Åctivity",
            "com.example.åctivity",
            "com.example.😀Receiver",
            "a\u0000b",
            "com.K.Service",
            "Straße",
            "STRASSE",
            "İıI",
            "Σσς",
            "𐐀𐐨",
            "ﬀ",
            "",
        ) + List(250) { generated(16) }
        val keywords = listOf(
            "com.google.firebase.firebase_ads.",
            "com.google.firebase.firebase_analytics",
            "com.tencent.mm.plugin.location_soso.",
            "com.tencent.mm.plugin.location_google",
            "com.openx.android_sdk_openx",
            "com.tencent.mm.plugin.nfc_open.ui.NfcWebViewUI",
            "com.google.firebase",
            "com.example._Receiver",
            "Å",
            "å",
            "a\u0000b",
            "\u0000",
            "",
            "%",
            "_",
            "a%b_a",
            "com.k",
            "straße",
            "strasse",
            "i",
            "σ",
            "𐐨",
            "ff",
        ) + List(150) { generated(7) }
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE components(name TEXT NOT NULL)")
            db.beginTransaction()
            try {
                db.compileStatement("INSERT INTO components VALUES (?)").use { statement ->
                    names.forEach {
                        statement.bindString(1, it)
                        statement.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            keywords.forEach { keyword ->
                val expected = mutableSetOf<String>()
                db.rawQuery("SELECT name FROM components WHERE name LIKE '%' || ? || '%'", arrayOf(keyword)).use { cursor ->
                    while (cursor.moveToNext()) expected.add(cursor.getString(0))
                }
                val pattern = SqlLikePattern(keyword)
                assertEquals(expected, names.filter { pattern.matches(it) }.toSet(), "Keyword: $keyword")
            }
        }
    }
}
