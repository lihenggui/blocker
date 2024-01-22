/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.domain.components

import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.IComponentDetailRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.toAppItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

class SearchComponentsUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val componentDetailRepository: IComponentDetailRepository,
    private val getServiceController: GetServiceControllerUseCase,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String, keyword: String): Flow<ComponentSearchResult> {
        return combineTransform(
            getServiceController(),
            appRepository.getApplication(packageName),
            componentRepository.getComponentList(packageName),
        ) { serviceController, app, components ->
            if (app == null) {
                Timber.w("Cannot find app with package name $packageName, return empty result")
                emit(ComponentSearchResult(app = null))
                return@combineTransform
            }
            val receiver = components.filter { it.type == RECEIVER }
            val service = components.filter { it.type == SERVICE }
            val activity = components.filter { it.type == ACTIVITY }
            val provider = components.filter { it.type == PROVIDER }
            val searchKeywords = keyword.split(",")
                .map { it.trim() }
                .filterNot { it.isEmpty() }
            if (searchKeywords.isEmpty()) {
                emit(
                    ComponentSearchResult(
                        app = app.toAppItem(),
                        activity = activity.getComponentDescription(),
                        service = service.getComponentDescription()
                            .getServiceStatus(serviceController),
                        receiver = receiver.getComponentDescription(),
                        provider = provider.getComponentDescription(),
                    ),
                )
                return@combineTransform
            }
            // Search keyword is not empty, filter components
            val filteredReceiver = findMatchedComponent(receiver, searchKeywords)
            val filteredService = findMatchedComponent(service, searchKeywords)
            val filteredActivity = findMatchedComponent(activity, searchKeywords)
            val filteredProvider = findMatchedComponent(provider, searchKeywords)
            emit(
                ComponentSearchResult(
                    app = app.toAppItem(),
                    activity = filteredActivity.getComponentDescription(),
                    service = filteredService.getComponentDescription()
                        .getServiceStatus(serviceController),
                    receiver = filteredReceiver.getComponentDescription(),
                    provider = filteredProvider.getComponentDescription(),
                ),
            )
        }
            .flowOn(cpuDispatcher)
    }

    private fun findMatchedComponent(
        components: List<ComponentInfo>,
        keywords: List<String>,
    ): List<ComponentInfo> {
        return components.filter { component ->
            keywords.any { keyword ->
                component.name.contains(keyword, ignoreCase = true)
            }
        }
    }

    private suspend fun List<ComponentInfo>.getComponentDescription(): List<ComponentInfo> {
        return map {
            val componentDetail = componentDetailRepository.getLocalComponentDetail(it.name)
                .first()
            it.copy(description = componentDetail?.description)
        }
    }

    private suspend fun List<ComponentInfo>.getServiceStatus(serviceController: IServiceController): List<ComponentInfo> {
        return map {
            it.copy(isRunning = serviceController.isServiceRunning(it.packageName, it.name))
        }
    }
}
