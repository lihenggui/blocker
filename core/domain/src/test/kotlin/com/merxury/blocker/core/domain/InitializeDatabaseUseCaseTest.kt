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

import app.cash.turbine.test
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.preference.AppPropertiesData
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class InitializeDatabaseUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val componentRepository = TestComponentRepository()
    private val appPropertiesRepository = TestAppPropertiesRepository()
    private val initializeDatabaseUseCase = InitializeDatabaseUseCase(
        appRepository = appRepository,
        componentRepository = componentRepository,
        appPropertiesRepository = appPropertiesRepository,
    )

    @Test
    fun givenComponentDatabaseInitialized_returnInitializeStateDone() = runTest {
        appRepository.sendAppList(sampleAppList)
        userDataRepository.sendUserData(defaultUserData)
        appPropertiesRepository.sendAppProperties(AppPropertiesData(componentDatabaseInitialized = true))
        componentRepository.sendComponentList(sampleComponentList)
        initializeDatabaseUseCase().test {
            assertEquals(InitializeState.Done, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenComponentDatabaseNotInitialized_returnInitializingComponentThenDone() = runTest {
        appRepository.sendAppList(sampleAppList)
        userDataRepository.sendUserData(defaultUserData)
        appPropertiesRepository.sendAppProperties(AppPropertiesData())
        componentRepository.sendComponentList(sampleComponentList)
        initializeDatabaseUseCase().test {
            sampleAppList.forEach {
                assertEquals(InitializeState.Initializing(it.label), awaitItem())
            }
            assertEquals(InitializeState.Done, awaitItem())
        }
    }
}

private val sampleAppList = listOf(
    InstalledApp(
        label = "App1",
        packageName = "com.merxury.test1",
    ),
    InstalledApp(
        label = "App2",
        packageName = "com.merxury.test2",
    ),
    InstalledApp(
        label = "App3",
        packageName = "com.merxury.test3",
    ),
)

private val sampleComponentList = listOf(
    ComponentInfo(
        simpleName = "Activity1",
        name = "com.merxury.blocker.test.activity1",
        packageName = "com.merxury.test1",
        type = ACTIVITY,
        description = "An example activity",
    ),
    ComponentInfo(
        simpleName = "Service1",
        name = "com.merxury.blocker.test.service1",
        packageName = "com.merxury.test1",
        type = SERVICE,
        description = "An example service",
        pmBlocked = true,
    ),
    ComponentInfo(
        simpleName = "Receiver1",
        name = "com.merxury.blocker.test.receiver1",
        packageName = "com.merxury.test1",
        type = RECEIVER,
        description = "An example receiver",
    ),
    ComponentInfo(
        simpleName = "Provider1",
        name = "com.merxury.blocker.test.provider1",
        packageName = "com.merxury.test1",
        type = PROVIDER,
        description = "An example provider",
    ),
)
