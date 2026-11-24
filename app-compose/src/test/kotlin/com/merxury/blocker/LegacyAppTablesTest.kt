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

package com.merxury.blocker

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.preference.AppPropertiesData
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant
import com.merxury.blocker.core.model.data.InstalledApp as OldInstalledApp
import com.merxury.blocker.core.model.system.InstalledApp as CachedApp

class LegacyAppTablesTest {
    private val appRepository = TestAppRepository()
    private val appProperties = TestAppPropertiesRepository()
    private val components = TestComponentRepository().apply { sendComponentList(emptyList()) }
    private val installedApps = FakeInstalledAppRepository()
    private val tables = LegacyAppTables(
        appRepository = appRepository,
        initializeDatabase = InitializeDatabaseUseCase(appRepository, components, appProperties),
        installedApps = installedApps,
        selectedUsers = FakeSelectedUserRepository(OWNER),
    )

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tables.keepFilled() }
    }

    @Test
    fun givenComponentsNeverScanned_whenStarted_thenAppTableFilledAndComponentsScanned() = runTest {
        appProperties.sendAppProperties(AppPropertiesData())
        appRepository.sendAppList(listOf(OldInstalledApp(packageName = "com.example", label = "Example")))

        start()

        assertThat(appRepository.updateApplicationListCount).isAtLeast(1)
        assertThat(appProperties.appProperties.first().componentDatabaseInitialized).isTrue()
    }

    @Test
    fun givenStarted_whenOwnUsersAppsChange_thenAppTableFilledAgain() = runTest {
        appProperties.sendAppProperties(AppPropertiesData(componentDatabaseInitialized = true))
        start()
        val before = appRepository.updateApplicationListCount

        installedApps.sendApps(listOf(cachedApp("com.new", OWNER)))

        assertThat(appRepository.updateApplicationListCount).isEqualTo(before + 1)
    }

    @Test
    fun givenStarted_whenOnlyAnotherUsersAppsChange_thenAppTableNotFilledAgain() = runTest {
        appProperties.sendAppProperties(AppPropertiesData(componentDatabaseInitialized = true))
        start()
        val before = appRepository.updateApplicationListCount

        installedApps.sendApps(listOf(cachedApp("com.new", WORK)))

        assertThat(appRepository.updateApplicationListCount).isEqualTo(before)
    }

    private fun cachedApp(packageName: String, user: UserId) = CachedApp(
        key = AppKey(packageName, user),
        uid = null,
        label = packageName,
        versionName = "1",
        versionCode = 1,
        isSystem = false,
        enabled = true,
        enabledSetting = AppEnabledSetting.DEFAULT,
        apkPath = null,
        firstInstallTime = Instant.fromEpochMilliseconds(0),
        lastUpdateTime = Instant.fromEpochMilliseconds(0),
        minSdk = 24,
        targetSdk = 34,
    )

    private companion object {
        val OWNER = UserId(0)
        val WORK = UserId(10)
    }
}
