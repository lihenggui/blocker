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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.model.data.GeneralRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject

class SearchGeneralRuleUseCase @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository,
    private val userDataRepository: UserDataRepository,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
) {
    operator fun invoke(keyword: String = ""): Flow<List<GeneralRule>> {
        val searchFlow = generalRuleRepository.searchGeneralRule(keyword)
        val userDataFlow = userDataRepository.userData
        val filesDir = filesDir.resolve(ruleBaseFolder)
        return combine(searchFlow, userDataFlow) { list, _ ->
            list.map { rule ->
                val iconPath = rule.iconUrl
                if (iconPath.isNullOrEmpty()) {
                    rule
                } else {
                    rule.copy(iconUrl = filesDir.resolve(iconPath).absolutePath)
                }
            }.sortedByDescending { it.matchedAppCount }
        }
    }
}
