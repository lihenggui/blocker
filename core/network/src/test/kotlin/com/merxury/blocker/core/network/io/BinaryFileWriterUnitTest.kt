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

import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class BinaryFileWriterUnitTest {
    private val inputStream = PipedInputStream()
    private val outputStream = PipedOutputStream(inputStream)

    @Test
    fun givenInputStream_whenWrite_thenExpectWritten() {
        val content = "Hello"
        BinaryFileWriter(outputStream).use {
            it.write(content.byteInputStream(), content.length.toLong())
        }
        assert(inputStream.readBytes().contentEquals(content.toByteArray()))
    }

    @Test
    fun givenInputStreamEmpty_whenWrite_thenExpectNotWritten() {
        val content = ""
        BinaryFileWriter(outputStream).use {
            it.write(content.byteInputStream(), content.length.toLong())
        }
        assert(inputStream.readBytes().contentEquals(content.toByteArray()))
    }
}
