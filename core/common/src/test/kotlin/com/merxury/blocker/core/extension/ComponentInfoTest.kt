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

package com.merxury.blocker.core.extension

import android.content.pm.ComponentInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ComponentInfoTest {

    private fun createComponentInfo(name: String): ComponentInfo {
        val info = ComponentInfo()
        info.name = name
        return info
    }

    @Test
    fun givenFullyQualifiedName_whenGetSimpleName_thenReturnsLastSegment() {
        val info = createComponentInfo("com.example.app.MyActivity")
        assertEquals("MyActivity", info.getSimpleName())
    }

    @Test
    fun givenSimpleName_whenGetSimpleName_thenReturnsItself() {
        val info = createComponentInfo("MyActivity")
        assertEquals("MyActivity", info.getSimpleName())
    }

    @Test
    fun givenNameWithSingleDot_whenGetSimpleName_thenReturnsLastSegment() {
        val info = createComponentInfo("app.MyActivity")
        assertEquals("MyActivity", info.getSimpleName())
    }

    @Test
    fun givenEmptyString_whenGetSimpleName_thenReturnsEmpty() {
        val info = createComponentInfo("")
        assertEquals("", info.getSimpleName())
    }
}
