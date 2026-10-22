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

package com.merxury.blocker.core.database.generalrule

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.data.GeneralRule
import org.junit.Test

class GeneralRuleEntityTest {

    @Test
    fun givenEntityWithNetworkSignatureAndWebsite_whenMappedToExternalModelAndBack_thenPreservesBothFields() {
        val entity = GeneralRuleEntity(
            id = 1,
            name = "Rule",
            networkSignature = listOf("api.example.com", "ads.example.com"),
            website = "https://example.com",
            matchedAppCount = 7,
        )

        val model = entity.asExternalModel()

        assertThat(model.networkSignature).containsExactlyElementsIn(entity.networkSignature).inOrder()
        assertThat(model.website).isEqualTo(entity.website)
        assertThat(model.fromExternalModel()).isEqualTo(entity)
    }

    @Test
    fun givenExternalModelWithNetworkSignatureAndWebsite_whenMappedToEntityAndBack_thenPreservesBothFields() {
        val model = GeneralRule(
            id = 1,
            name = "Rule",
            networkSignature = listOf("api.example.com", "ads.example.com"),
            website = "https://example.com",
            matchedAppCount = 7,
        )

        val entity = model.fromExternalModel()

        assertThat(entity.networkSignature).containsExactlyElementsIn(model.networkSignature).inOrder()
        assertThat(entity.website).isEqualTo(model.website)
        assertThat(entity.asExternalModel()).isEqualTo(model)
    }

    @Test
    fun givenEmptyNetworkSignatureAndNullWebsite_whenRoundTripped_thenPreservesEmptyValues() {
        val model = GeneralRule(id = 1, name = "Rule")

        assertThat(model.fromExternalModel().asExternalModel()).isEqualTo(model)
    }
}
