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

package com.merxury.blocker.core.domain.controller

import app.cash.turbine.test
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.IFW_PLUS_PM
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.testing.controller.FakeController
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GetControllerUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val rootController = FakeController()
    private val ifwController = FakeController()
    private val shizukuController = FakeController()
    private val combinedController = FakeController()

    private val getControllerUseCase = GetControllerUseCase(
        userDataRepository,
        rootController,
        ifwController,
        shizukuController,
        combinedController,
    )

    @Test
    fun whenSetIfwType_getIfwController() = runTest {
        userDataRepository.setControllerType(IFW)
        val controller = getControllerUseCase().first()
        assert(controller === ifwController)
    }

    @Test
    fun whenSetPmType_getRootController() = runTest {
        userDataRepository.setControllerType(PM)
        val controller = getControllerUseCase().first()
        assert(controller === rootController)
    }

    @Test
    fun whenSetShizukuType_getShizukuController() = runTest {
        userDataRepository.setControllerType(SHIZUKU)
        val controller = getControllerUseCase().first()
        assert(controller === shizukuController)
    }

    @Test
    fun whenSetIfwPlusPmType_getCombinedController() = runTest {
        userDataRepository.setControllerType(IFW_PLUS_PM)
        val controller = getControllerUseCase().first()
        assert(controller === combinedController)
    }

    @Test
    fun whenSetTypeSequentially_receiveCorrectControllersInFlow() = runTest {
        getControllerUseCase().test {
            userDataRepository.setControllerType(IFW)
            assertEquals(ifwController, awaitItem())
            userDataRepository.setControllerType(PM)
            assertEquals(rootController, awaitItem())
            userDataRepository.setControllerType(SHIZUKU)
            assertEquals(shizukuController, awaitItem())
            userDataRepository.setControllerType(IFW_PLUS_PM)
            assertEquals(combinedController, awaitItem())
        }
    }
}
