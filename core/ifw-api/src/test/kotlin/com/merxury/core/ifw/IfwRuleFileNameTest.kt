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

package com.merxury.core.ifw

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class IfwRuleFileNameTest {

    @Test
    fun givenPackageNames_whenChecked_thenPackageAndRuleFiles() {
        listOf("com.example", "a", "com.example_1.app", "android", "Com.Example").forEach {
            assertWithMessage(it).that(isPackageRuleFileName(it)).isTrue()
            assertWithMessage(it).that(isRuleFileName(it)).isTrue()
        }
    }

    @Test
    fun givenNamesThatAreNotPackages_whenChecked_thenRuleFilesButNotPackageFiles() {
        listOf("my-rules", "my rules", "1app", "com.1app", "_x", "com._x", "com.", "com..x", "custom..rules", "a..", "é").forEach {
            assertWithMessage(it).that(isPackageRuleFileName(it)).isFalse()
            assertWithMessage(it).that(isRuleFileName(it)).isTrue()
        }
    }

    @Test
    fun givenNamesThatLeaveTheDirectoryOrHide_whenChecked_thenRejectedByBoth() {
        listOf("", ".", "..", "../x", "a/b", "/abs", ".blocker-ifw-1", ".hidden..x", "a\u0000b").forEach {
            assertWithMessage(it).that(isPackageRuleFileName(it)).isFalse()
            assertWithMessage(it).that(isRuleFileName(it)).isFalse()
        }
    }

    @Test
    fun givenWakeFeatureFiles_whenChecked_thenRejectedByBoth() {
        listOf(
            "blocker-managed-wake",
            "blocker-user-wake",
            "blocker-wake-capability-probe",
            "blocker-managed-wake-com.merxury.blocker.debug",
            "blocker-user-wake-com.merxury.blocker.debug",
            "blocker-wake-capability-probe-com.merxury.blocker.debug",
        ).forEach {
            assertWithMessage(it).that(isPackageRuleFileName(it)).isFalse()
            assertWithMessage(it).that(isRuleFileName(it)).isFalse()
        }
    }
}
