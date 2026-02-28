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

import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GatherAllMatchedComponentsUseCase @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository,
    private val componentRepository: ComponentRepository,
    private val appRepository: AppRepository,
    private val userDataRepository: UserDataRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<List<ComponentInfo>> = flow {
        val userData = userDataRepository.userData.first()
        val rules = generalRuleRepository.getGeneralRules().first()
        val seenKeys = mutableSetOf<String>()
        val allComponents = mutableListOf<ComponentInfo>()
        rules.filter { it.matchedAppCount > 0 }
            .forEach { rule ->
                rule.searchKeyword.forEach { keyword ->
                    val components = componentRepository.searchComponent(keyword).first()
                    components.forEach { component ->
                        val key = "${component.packageName}/${component.name}"
                        if (seenKeys.add(key)) {
                            allComponents.add(component)
                        }
                    }
                }
            }
        if (!userData.showSystemApps) {
            val packagesToRemove = mutableSetOf<String>()
            allComponents.map { it.packageName }
                .distinct()
                .forEach { packageName ->
                    val appInfo = appRepository.getApplication(packageName).first()
                        ?: return@forEach
                    if (appInfo.isSystem) {
                        packagesToRemove.add(packageName)
                    }
                }
            allComponents.removeAll { it.packageName in packagesToRemove }
        }
        emit(allComponents.toList())
    }
        .flowOn(ioDispatcher)
}
