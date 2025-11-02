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

package com.merxury.blocker.core.data.respository.sharetarget

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
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

class LocalShareTargetRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val localDataSource: LocalShareTargetDataSource = mock()
    private val cacheDataSource: CacheShareTargetDataSource = mock()
    private val userDataRepository = TestUserDataRepository()
    private val pm: PackageManager = mock()

    private lateinit var repository: LocalShareTargetRepository

    private val testEntities = listOf(
        ShareTargetActivityEntity(
            packageName = "com.android.system",
            componentName = "com.android.system.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "System Share",
            ifwBlocked = false,
            pmBlocked = false,
            exported = true,
            label = null,
            intentFilters = emptyList(),
        ),
        ShareTargetActivityEntity(
            packageName = "com.example.userapp",
            componentName = "com.example.userapp.SendActivity",
            simpleName = "SendActivity",
            displayName = "User Share",
            ifwBlocked = false,
            pmBlocked = false,
            exported = true,
            label = null,
            intentFilters = emptyList(),
        ),
    )

    @Before
    fun setup() {
        repository = LocalShareTargetRepository(
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
    fun givenCachedData_whenGetShareTargetActivities_thenEmitsCacheThenUpdates() = runTest {
        val cachedEntities = listOf(testEntities.first())
        val latestEntities = testEntities

        whenever(cacheDataSource.getShareTargetActivities()).doReturn(flowOf(cachedEntities))
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(latestEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getShareTargetActivities().test {
            val firstEmission = awaitItem()
            assertEquals(1, firstEmission.size)
            assertEquals("com.android.system", firstEmission.first().packageName)

            verify(cacheDataSource).updateActivities(latestEntities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenShowSystemAppsFalse_whenGetShareTargetActivities_thenFiltersSystemApps() = runTest {
        whenever(cacheDataSource.getShareTargetActivities()).doReturn(flowOf(testEntities))
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(testEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = false))

        repository.getShareTargetActivities().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("com.example.userapp", result.first().packageName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenShowSystemAppsTrue_whenGetShareTargetActivities_thenIncludesAllApps() = runTest {
        whenever(cacheDataSource.getShareTargetActivities()).doReturn(flowOf(testEntities))
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(testEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getShareTargetActivities().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenEntities_whenGetShareTargetActivities_thenConvertsToComponentInfo() = runTest {
        val singleEntity = listOf(testEntities.first())
        whenever(cacheDataSource.getShareTargetActivities()).doReturn(flowOf(singleEntity))
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(singleEntity))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getShareTargetActivities().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("com.android.system", result.first().packageName)
            assertEquals("com.android.system.ShareActivity", result.first().componentName)
            assertEquals("ShareActivity", result.first().simpleName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUserDataChanges_whenGetShareTargetActivities_thenReactsToChanges() = runTest {
        whenever(cacheDataSource.getShareTargetActivities()).doReturn(flowOf(testEntities))
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(testEntities))

        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))

        repository.getShareTargetActivities().test {
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
    fun givenLatestData_whenUpdateShareTargetActivities_thenUpdatesDatabase() = runTest {
        val latestEntities = testEntities
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(latestEntities))

        val result = repository.updateShareTargetActivities().first()

        assertTrue(result is Result.Success)
        verify(cacheDataSource).updateActivities(latestEntities)
    }

    @Test
    fun givenUpdateCall_whenUpdateShareTargetActivities_thenCompletesAfterOneEmission() = runTest {
        val latestEntities = testEntities
        whenever(localDataSource.getShareTargetActivities()).doReturn(flowOf(latestEntities))

        repository.updateShareTargetActivities().test {
            val result = awaitItem()
            assertTrue(result is Result.Success)
            awaitComplete()
        }

        verify(cacheDataSource).updateActivities(latestEntities)
    }
}
