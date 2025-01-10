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
import com.merxury.blocker.core.data.respository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import timber.log.Timber
import javax.inject.Inject

class InitializeDatabaseUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val appPropertiesRepository: AppPropertiesRepository,
) {
    operator fun invoke(): Flow<InitializeState> = flow {
        val appProperties = appPropertiesRepository.appProperties.first()
        if (appProperties.componentDatabaseInitialized) {
            Timber.v("Component database already initialized")
            emit(InitializeState.Done)
        } else {
            Timber.v("Start initializing component database")
            initComponentDatabaseTask()
                .collect { emit(it) }
        }
    }

    private fun initComponentDatabaseTask() = appRepository.getApplicationList()
        .transform { installedAppList ->
            if (installedAppList.isEmpty()) {
                return@transform
            }
            installedAppList.forEach {
                val packageName = it.packageName
                emit(InitializeState.Initializing(it.label))
                combine(
                    componentRepository.getComponentList(packageName, ACTIVITY),
                    componentRepository.getComponentList(packageName, SERVICE),
                    componentRepository.getComponentList(packageName, RECEIVER),
                    componentRepository.getComponentList(packageName, PROVIDER),
                ) { activities, services, receivers, providers ->
                    val components = activities + services + receivers + providers
                    componentRepository.saveComponents(components)
                    Timber.v("Initialized $packageName, ${components.size} components")
                }
                    .first()
            }
            appPropertiesRepository.markComponentDatabaseInitialized()
            emit(InitializeState.Done)
        }
}
