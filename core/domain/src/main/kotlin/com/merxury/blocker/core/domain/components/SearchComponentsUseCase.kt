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
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentShowPriority.DISABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.ENABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.NONE
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.ComponentSorting.PACKAGE_NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.SortingOrder.DESCENDING
import com.merxury.blocker.core.model.preference.UserPreferenceData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

class SearchComponentsUseCase @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val componentDetailRepository: ComponentDetailRepository,
    private val getServiceController: GetServiceControllerUseCase,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String, keyword: String): Flow<ComponentSearchResult> {
        Timber.v("Search components for package $packageName with keyword $keyword")
        return combineTransform(
            userDataRepository.userData,
            getServiceController(),
            appRepository.getApplication(packageName),
            componentRepository.getComponentList(packageName),
        ) { userData, serviceController, app, components ->
            Timber.v("Received ${components.size} components for package $packageName")
            if (app == null) {
                Timber.w("Cannot find app with package name $packageName, return empty result")
                emit(ComponentSearchResult(app = null))
                return@combineTransform
            }
            val receiver = filterAndSortComponentList(components, userData, RECEIVER)
            val service = filterAndSortComponentList(components, userData, SERVICE)
            val activity = filterAndSortComponentList(components, userData, ACTIVITY)
            val provider = filterAndSortComponentList(components, userData, PROVIDER)
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
            val filteredReceiver = searchMatchedComponent(receiver, searchKeywords)
            val filteredService = searchMatchedComponent(service, searchKeywords)
            val filteredActivity = searchMatchedComponent(activity, searchKeywords)
            val filteredProvider = searchMatchedComponent(provider, searchKeywords)
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

    private fun filterAndSortComponentList(
        list: List<ComponentInfo>,
        userData: UserPreferenceData,
        type: ComponentType,
    ): List<ComponentInfo> {
        val componentSorting = userData.componentSorting
        val componentSortingOrder = userData.componentSortingOrder
        val componentShowPriority = userData.componentShowPriority
        return list.filter { it.type == type }
            .sortBy(componentSorting, componentSortingOrder)
            .sortByPriority(componentShowPriority)
            .showRunningServicesOnTop()
    }

    private fun searchMatchedComponent(
        components: List<ComponentInfo>,
        keywords: List<String>,
    ): List<ComponentInfo> {
        return components.filter { component ->
            keywords.any { keyword ->
                component.name.contains(keyword, ignoreCase = true)
            }
        }
    }

    private suspend inline fun List<ComponentInfo>.getComponentDescription(): List<ComponentInfo> {
        return map {
            val componentDetail = componentDetailRepository.getLocalComponentDetail(it.name)
                .first()
            it.copy(description = componentDetail?.description)
        }
    }

    private fun List<ComponentInfo>.getServiceStatus(
        serviceController: IServiceController,
    ): List<ComponentInfo> {
        return map {
            it.copy(isRunning = serviceController.isServiceRunning(it.packageName, it.name))
        }
    }

    private fun List<ComponentInfo>.sortBy(
        sortBy: ComponentSorting,
        order: SortingOrder,
    ): List<ComponentInfo> {
        return when (sortBy) {
            COMPONENT_NAME -> when (order) {
                ASCENDING -> sortedBy { it.simpleName.lowercase() }
                DESCENDING -> sortedByDescending { it.simpleName.lowercase() }
            }

            PACKAGE_NAME -> when (order) {
                ASCENDING -> sortedBy { it.name.lowercase() }
                DESCENDING -> sortedByDescending { it.name.lowercase() }
            }
        }
    }

    private fun List<ComponentInfo>.sortByPriority(
        priority: ComponentShowPriority,
    ): List<ComponentInfo> {
        return when (priority) {
            NONE -> this
            DISABLED_COMPONENTS_FIRST -> sortedBy { it.enabled() }
            ENABLED_COMPONENTS_FIRST -> sortedByDescending { it.enabled() }
        }
    }

    private fun List<ComponentInfo>.showRunningServicesOnTop(): List<ComponentInfo> {
        return sortedByDescending { it.isRunning }
    }
}
