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

package com.merxury.blocker.core.data.util

import app.cash.turbine.test
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeController
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.controller.FakeShizukuInitializer
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class AppPermissionMonitorTest {
    @Test
    fun givenShizukuSystemUid_whenObservePermission_thenSystemUser() = runTest {
        val userDataRepository = TestUserDataRepository()
        val monitor = AppPermissionMonitor(
            userDataRepository = userDataRepository,
            shizukuInitializer = FakeShizukuInitializer(shizukuUid = SYSTEM_UID),
            rootApiController = FakeController(rootGranted = true),
            rootApiAppController = FakeAppController(rootGranted = true),
            rootApiServiceController = FakeServiceController(),
            appScope = backgroundScope,
        )

        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.SYSTEM_USER, awaitItem())
        }
    }

    private companion object {
        const val SYSTEM_UID = 1000
    }
}
