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

package com.merxury.blocker.core.domain.controller

import app.cash.turbine.test
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GetServiceControllerUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val rootServiceController = FakeServiceController()
    private val shizukuServiceController = FakeServiceController()
    private val getServiceControllerUseCase = GetServiceControllerUseCase(
        userDataRepository,
        rootServiceController,
        shizukuServiceController,
    )

    @Test
    fun whenSetIfwType_getRootServiceController() = runTest {
        userDataRepository.setControllerType(IFW)
        val controller = getServiceControllerUseCase().first()
        assert(controller === rootServiceController)
    }

    @Test
    fun whenSetPmType_getRootServiceController() = runTest {
        userDataRepository.setControllerType(PM)
        val controller = getServiceControllerUseCase().first()
        assert(controller === rootServiceController)
    }

    @Test
    fun whenSetShizukuType_getShizukuServiceController() = runTest {
        userDataRepository.setControllerType(SHIZUKU)
        val controller = getServiceControllerUseCase().first()
        assert(controller === shizukuServiceController)
    }

    @Test
    fun whenSetTypeSequentially_receiveCorrectServiceControllersInFlow() = runTest {
        getServiceControllerUseCase().test {
            userDataRepository.setControllerType(IFW)
            assertEquals(rootServiceController, awaitItem())
            userDataRepository.setControllerType(PM)
            assertEquals(rootServiceController, awaitItem())
            userDataRepository.setControllerType(SHIZUKU)
            assertEquals(shizukuServiceController, awaitItem())
        }
    }
}
