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

package com.merxury.blocker.core.data.respository.generalrule

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.generalrule.asExternalModel
import com.merxury.blocker.core.database.generalrule.fromExternalModel
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import kotlinx.serialization.json.Json
import org.junit.Test

class GeneralRuleMappingTest {

    @Test
    fun givenRuleJsonWithNetworkSignatureAndWebsite_whenMappedToCacheAndBack_thenPreservesBothFields() {
        val rules = Json.decodeFromString<List<NetworkGeneralRule>>(
            """
            [{
                "id": 1,
                "name": "Rule",
                "networkSignature": ["api.example.com", "ads.example.com"],
                "website": "https://example.com"
            }]
            """.trimIndent(),
        )
        val expected = GeneralRule(
            id = 1,
            name = "Rule",
            networkSignature = listOf("api.example.com", "ads.example.com"),
            website = "https://example.com",
        )

        val model = rules.single().asExternalModel()

        assertThat(model).isEqualTo(expected)
        assertThat(model.fromExternalModel().asExternalModel()).isEqualTo(expected)
    }

    @Test
    fun givenRuleJsonWithoutOptionalFields_whenDecoded_thenUsesEmptyNetworkSignatureAndNullWebsite() {
        val rule = Json.decodeFromString<NetworkGeneralRule>("""{"id": 1, "name": "Rule"}""")

        assertThat(rule.asExternalModel()).isEqualTo(GeneralRule(id = 1, name = "Rule"))
    }
}
