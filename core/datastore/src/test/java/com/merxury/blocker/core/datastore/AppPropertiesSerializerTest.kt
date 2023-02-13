/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class AppPropertiesSerializerTest {
    private val appPropertiesSerializer = AppPropertiesSerializer()

    @Test
    fun defaultAppProperties_isEmpty() {
        assertEquals(
            appProperties {
                // Default value
            },
            appPropertiesSerializer.defaultValue,
        )
    }

    @Test
    fun writingAndReadingAppProperties_outputsCorrectValue() = runTest {
        val expectedAppProperties = appProperties {
            generalRuleDatabaseInitialized = true
            componentDatabaseInitialized = true
        }

        val outputStream = ByteArrayOutputStream()

        expectedAppProperties.writeTo(outputStream)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())

        val actualAppProperties = appPropertiesSerializer.readFrom(inputStream)

        assertEquals(
            expectedAppProperties,
            actualAppProperties,
        )
    }

    @Test(expected = CorruptionException::class)
    fun readingInvalidAppProperties_throwsCorruptionException() = runTest {
        appPropertiesSerializer.readFrom(ByteArrayInputStream(byteArrayOf(0)))
    }
}
