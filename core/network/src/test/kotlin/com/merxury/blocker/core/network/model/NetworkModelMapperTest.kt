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

package com.merxury.blocker.core.network.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkModelMapperTest {

    @Test
    fun givenNetworkComponentDetail_whenAsExternalModel_thenAllFieldsMapped() {
        val network = NetworkComponentDetail(
            name = "com.example.Service",
            sdkName = "ExampleSDK",
            description = "A test service",
            disableEffect = "No effect",
            contributor = "tester",
            addedVersion = "1.0",
            recommendToBlock = true,
        )
        val external = network.asExternalModel()
        assertThat(external.name).isEqualTo("com.example.Service")
        assertThat(external.sdkName).isEqualTo("ExampleSDK")
        assertThat(external.description).isEqualTo("A test service")
        assertThat(external.disableEffect).isEqualTo("No effect")
        assertThat(external.contributor).isEqualTo("tester")
        assertThat(external.addedVersion).isEqualTo("1.0")
        assertThat(external.recommendToBlock).isTrue()
    }

    @Test
    fun givenNetworkComponentDetailWithNulls_whenAsExternalModel_thenNullsPreserved() {
        val network = NetworkComponentDetail(name = "com.example.Service")
        val external = network.asExternalModel()
        assertThat(external.name).isEqualTo("com.example.Service")
        assertThat(external.sdkName).isNull()
        assertThat(external.description).isNull()
        assertThat(external.disableEffect).isNull()
        assertThat(external.contributor).isNull()
        assertThat(external.addedVersion).isNull()
        assertThat(external.recommendToBlock).isFalse()
    }

    @Test
    fun givenComponentDetail_whenAsNetworkModel_thenRoundTrips() {
        val original = NetworkComponentDetail(
            name = "com.example.Receiver",
            sdkName = "SDK",
            description = "desc",
            disableEffect = "effect",
            contributor = "user",
            addedVersion = "2.0",
            recommendToBlock = false,
        )
        val roundTripped = original.asExternalModel().asNetworkModel()
        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun givenNetworkGeneralRule_whenAsExternalModel_thenAllFieldsMapped() {
        val network = NetworkGeneralRule(
            id = 1,
            name = "Test Rule",
            iconUrl = "https://example.com/icon.png",
            company = "TestCo",
            searchKeyword = listOf("keyword1", "keyword2"),
            useRegexSearch = true,
            description = "A test rule",
            safeToBlock = true,
            sideEffect = "None",
            contributors = listOf("user1", "user2"),
        )
        val external = network.asExternalModel()
        assertThat(external.id).isEqualTo(1)
        assertThat(external.name).isEqualTo("Test Rule")
        assertThat(external.iconUrl).isEqualTo("https://example.com/icon.png")
        assertThat(external.company).isEqualTo("TestCo")
        assertThat(external.searchKeyword).containsExactly("keyword1", "keyword2")
        assertThat(external.useRegexSearch).isTrue()
        assertThat(external.description).isEqualTo("A test rule")
        assertThat(external.safeToBlock).isTrue()
        assertThat(external.sideEffect).isEqualTo("None")
        assertThat(external.contributors).containsExactly("user1", "user2")
    }

    @Test
    fun givenNetworkGeneralRuleWithDefaults_whenAsExternalModel_thenDefaultsPreserved() {
        val network = NetworkGeneralRule(id = 42, name = "Minimal Rule")
        val external = network.asExternalModel()
        assertThat(external.id).isEqualTo(42)
        assertThat(external.name).isEqualTo("Minimal Rule")
        assertThat(external.iconUrl).isNull()
        assertThat(external.company).isNull()
        assertThat(external.searchKeyword).isEmpty()
        assertThat(external.useRegexSearch).isNull()
        assertThat(external.description).isNull()
        assertThat(external.safeToBlock).isNull()
        assertThat(external.sideEffect).isNull()
        assertThat(external.contributors).isEmpty()
    }
}
