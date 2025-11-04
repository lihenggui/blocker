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

package com.merxury.blocker.core.data.respository.debloater

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalDebloatableComponentRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val localDataSource: LocalDebloatableComponentDataSource = mock()
    private val cacheDataSource: CacheDebloatableComponentDataSource = mock()
    private val userDataRepository = TestUserDataRepository()
    private val pm: PackageManager = mock()

    private lateinit var repository: LocalDebloatableComponentRepository

    private val testEntities = listOf(
        DebloatableComponentEntity(
            packageName = "com.android.system",
            componentName = "com.android.system.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "System Share",
            ifwBlocked = false,
            pmBlocked = false,
            exported = true,
            label = null,
            type = ComponentType.ACTIVITY,
            intentFilters = emptyList(),
        ),
        DebloatableComponentEntity(
            packageName = "com.example.userapp",
            componentName = "com.example.userapp.SendActivity",
            simpleName = "SendActivity",
            displayName = "User Share",
            ifwBlocked = false,
            pmBlocked = false,
            exported = true,
            label = null,
            type = ComponentType.ACTIVITY,
            intentFilters = emptyList(),
        ),
    )

    @Before
    fun setup() {
        repository = LocalDebloatableComponentRepository(
            localDataSource = localDataSource,
            cacheDataSource = cacheDataSource,
            userDataRepository = userDataRepository,
            pm = pm,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

        whenever(pm.getApplicationInfo(any(), any<Int>())).thenAnswer { invocation ->
            val packageName = invocation.getArgument<String>(0)
            val appInfo = ApplicationInfo()
            appInfo.flags = if (packageName.startsWith("com.android")) {
                ApplicationInfo.FLAG_SYSTEM
            } else {
                0
            }
            appInfo
        }
    }

    @Test
    fun givenCachedData_whenGetDebloatableComponent_thenEmitsCacheThenUpdates() = runTest {
        val cachedEntities = listOf(testEntities.first())
        val latestEntities = testEntities

        whenever(cacheDataSource.getDebloatableComponent()).doReturn(flowOf(cachedEntities))
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(latestEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getDebloatableComponent().test {
            val firstEmission = awaitItem()
            assertEquals(1, firstEmission.size)
            assertEquals("com.android.system", firstEmission.first().packageName)

            verify(cacheDataSource).updateComponents(latestEntities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenShowSystemAppsFalse_whenGetDebloatableComponent_thenFiltersSystemApps() = runTest {
        whenever(cacheDataSource.getDebloatableComponent()).doReturn(flowOf(testEntities))
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(testEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = false))

        repository.getDebloatableComponent().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("com.example.userapp", result.first().packageName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenShowSystemAppsTrue_whenGetDebloatableComponent_thenIncludesAllApps() = runTest {
        whenever(cacheDataSource.getDebloatableComponent()).doReturn(flowOf(testEntities))
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(testEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getDebloatableComponent().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenEntities_whenGetDebloatableComponent_thenConvertsToComponentInfo() = runTest {
        val singleEntity = listOf(testEntities.first())
        whenever(cacheDataSource.getDebloatableComponent()).doReturn(flowOf(singleEntity))
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(singleEntity))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getDebloatableComponent().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("com.android.system", result.first().packageName)
            assertEquals("com.android.system.ShareActivity", result.first().componentName)
            assertEquals("ShareActivity", result.first().simpleName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserDataChanges_whenGetDebloatableComponent_thenReactsToChanges() = runTest {
        whenever(cacheDataSource.getDebloatableComponent()).doReturn(flowOf(testEntities))
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(testEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getDebloatableComponent().test {
            val firstEmission = awaitItem()
            assertEquals(2, firstEmission.size)

            userDataRepository.setShowSystemApps(false)

            val secondEmission = awaitItem()
            assertEquals(1, secondEmission.size)
            assertEquals("com.example.userapp", secondEmission.first().packageName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLatestData_whenUpdateDebloatableComponent_thenUpdatesDatabase() = runTest {
        val latestEntities = testEntities
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(latestEntities))

        val result = repository.updateDebloatableComponent().first()

        assertTrue(result is Result.Success)
        verify(cacheDataSource).updateComponents(latestEntities)
    }

    @Test
    fun givenUpdateCall_whenUpdateDebloatableComponent_thenCompletesAfterOneEmission() = runTest {
        val latestEntities = testEntities
        whenever(localDataSource.getDebloatableComponent()).doReturn(flowOf(latestEntities))

        repository.updateDebloatableComponent().test {
            val result = awaitItem()
            assertTrue(result is Result.Success)
            awaitComplete()
        }

        verify(cacheDataSource).updateComponents(latestEntities)
    }
}
