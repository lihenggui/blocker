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
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.preference.AppPropertiesData
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LegacyAppTablesTest {
    private val appRepository = TestAppRepository()
    private val appProperties = TestAppPropertiesRepository()
    private val components = TestComponentRepository().apply { sendComponentList(emptyList()) }
    private val tables = LegacyAppTables(
        appRepository = appRepository,
        initializeDatabase = InitializeDatabaseUseCase(appRepository, components, appProperties),
    )

    @Test
    fun givenComponentsNeverScanned_whenFilled_thenScansOnceAndReturns() = runTest {
        appProperties.sendAppProperties(AppPropertiesData())
        appRepository.sendAppList(listOf(InstalledApp(packageName = "com.example", label = "Example")))

        tables.fill()

        assertThat(appProperties.appProperties.first().componentDatabaseInitialized).isTrue()
    }

    @Test
    fun givenComponentsScanned_whenFilled_thenReturns() = runTest {
        appProperties.sendAppProperties(AppPropertiesData(componentDatabaseInitialized = true))

        tables.fill()

        assertThat(appProperties.appProperties.first().componentDatabaseInitialized).isTrue()
    }
}
