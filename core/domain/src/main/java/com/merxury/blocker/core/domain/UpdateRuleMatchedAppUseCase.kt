/*
 * Copyright 2023 Blocker
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

import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class UpdateRuleMatchedAppUseCase @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository,
    private val userDataRepository: UserDataRepository,
    private val componentRepository: ComponentRepository,
    private val appRepository: AppRepository,
) {

    operator fun invoke(id: Int): Flow<Unit> = flow {
        val rule = generalRuleRepository.getGeneralRule(id).first()
        val userData = userDataRepository.userData.first()
        val matchedComponents = mutableListOf<ComponentInfo>()
        rule.searchKeyword.forEach { keyword ->
            val components = componentRepository.searchComponent(keyword).first()
            matchedComponents.addAll(components)
        }
        val matchedGroup = matchedComponents.groupBy { it.packageName }
            .toMutableMap()
        val matchedPackages = matchedGroup.keys
        if (!userData.showSystemApps) {
            matchedPackages.forEach { packageName ->
                val appInfo = appRepository.getApplication(packageName).first()
                    ?: return@forEach
                if (appInfo.isSystem) {
                    matchedGroup.remove(packageName)
                }
            }
        }
        val updatedRule = rule.copy(matchedAppCount = matchedGroup.keys.size)
        generalRuleRepository.saveGeneralRule(updatedRule)
    }
}
