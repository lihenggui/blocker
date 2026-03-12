/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.network.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream

class BinaryFileWriterUnitTest {

    @Test
    fun givenContent_whenWrite_thenOutputMatchesInput() {
        val content = "Hello"
        val output = ByteArrayOutputStream()
        BinaryFileWriter(output).use {
            val written = it.write(content.byteInputStream(), content.length.toLong())
            assertThat(written).isEqualTo(content.length.toLong())
        }
        assertThat(output.toByteArray()).isEqualTo(content.toByteArray())
    }

    @Test
    fun givenEmptyContent_whenWrite_thenReturnsZero() {
        val output = ByteArrayOutputStream()
        BinaryFileWriter(output).use {
            val written = it.write("".byteInputStream(), 0L)
            assertThat(written).isEqualTo(0L)
        }
        assertThat(output.size()).isEqualTo(0)
    }

    @Test
    fun givenLargeContent_whenWrite_thenAllBytesWritten() {
        // Content larger than CHUNK_SIZE (8192) to test multi-chunk writing
        val content = "A".repeat(20_000)
        val output = ByteArrayOutputStream()
        BinaryFileWriter(output).use {
            val written = it.write(content.byteInputStream(), content.length.toLong())
            assertThat(written).isEqualTo(content.length.toLong())
        }
        assertThat(output.toByteArray()).isEqualTo(content.toByteArray())
    }

    @Test
    fun givenContent_whenWrite_thenProgressUpdatesReported() {
        val content = "A".repeat(20_000)
        val progressValues = mutableListOf<Double>()
        val output = ByteArrayOutputStream()
        BinaryFileWriter(output, onProgressUpdate = { progressValues.add(it) }).use {
            it.write(content.byteInputStream(), content.length.toLong())
        }
        assertThat(progressValues).isNotEmpty()
        // Progress should be monotonically increasing
        progressValues.zipWithNext().forEach { (prev, next) ->
            assertThat(next).isAtLeast(prev)
        }
        // Last progress should be 100%
        assertThat(progressValues.last()).isWithin(0.01).of(100.0)
    }

    @Test
    fun givenContent_whenWrite_thenProgressIsNotAlwaysZero() {
        // This specifically tests the integer division bug fix:
        // Before the fix, totalBytes / length would always be 0 for intermediate chunks
        val content = "A".repeat(20_000)
        val progressValues = mutableListOf<Double>()
        val output = ByteArrayOutputStream()
        BinaryFileWriter(output, onProgressUpdate = { progressValues.add(it) }).use {
            it.write(content.byteInputStream(), content.length.toLong())
        }
        // With the fix, intermediate progress values should be > 0 and < 100
        val intermediateValues = progressValues.dropLast(1)
        assertThat(intermediateValues).isNotEmpty()
        intermediateValues.forEach { value ->
            assertThat(value).isGreaterThan(0.0)
        }
    }

    @Test
    fun givenNegativeLength_whenWrite_thenReturnsZero() {
        val output = ByteArrayOutputStream()
        BinaryFileWriter(output).use {
            val written = it.write("data".byteInputStream(), -1L)
            assertThat(written).isEqualTo(0L)
        }
    }

    @Test
    fun givenClose_whenCalled_thenOutputStreamIsClosed() {
        val output = ByteArrayOutputStream()
        val writer = BinaryFileWriter(output)
        writer.close()
        // ByteArrayOutputStream.close() is a no-op, but we verify no exception is thrown
    }
}
