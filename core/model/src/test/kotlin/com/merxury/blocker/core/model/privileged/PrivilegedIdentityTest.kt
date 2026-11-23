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

package com.merxury.blocker.core.model.privileged

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrivilegedIdentityTest {

    @Test
    fun givenRoot_whenAskCapabilities_thenEverythingAllowed() {
        assertThat(PrivilegedIdentity.ROOT.canChangeComponents).isTrue()
        assertThat(PrivilegedIdentity.ROOT.canWriteSystemFiles).isTrue()
    }

    @Test
    fun givenShell_whenAskCapabilities_thenNeitherComponentsNorSystemFiles() {
        assertThat(PrivilegedIdentity.SHELL.canChangeComponents).isFalse()
        assertThat(PrivilegedIdentity.SHELL.canWriteSystemFiles).isFalse()
    }

    @Test
    fun givenSystem_whenAskCapabilities_thenComponentsAndSystemFiles() {
        assertThat(PrivilegedIdentity.SYSTEM.canChangeComponents).isTrue()
        assertThat(PrivilegedIdentity.SYSTEM.canWriteSystemFiles).isTrue()
    }
}
