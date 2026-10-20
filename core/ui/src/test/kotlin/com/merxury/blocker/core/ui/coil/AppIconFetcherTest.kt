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

package com.merxury.blocker.core.ui.coil

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
class AppIconFetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUp() {
        imageLoader = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(ICON_SIZE, true, context))
            }
            .build()
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun givenPackageInfo_whenLoadingIcon_thenReturnsConfiguredSize() = runTest {
        // Given
        val request = request(packageInfo())

        // When
        val result = imageLoader.execute(request)

        // Then
        assertIs<SuccessResult>(result)
        assertEquals(ICON_SIZE, result.image.width)
        assertEquals(ICON_SIZE, result.image.height)
        assertEquals(DataSource.DISK, result.dataSource)
    }

    @Test
    fun givenCachedPackageIcon_whenLoadingSamePackage_thenUsesMemoryCache() = runTest {
        // Given
        val first = assertIs<SuccessResult>(imageLoader.execute(request(packageInfo())))
        val request = request(packageInfo())

        // When
        val second = imageLoader.execute(request)

        // Then
        assertIs<SuccessResult>(second)
        assertEquals(first.memoryCacheKey, second.memoryCacheKey)
        assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
    }

    @Test
    fun givenCachedPackageIcon_whenLoadingUpdatedPackage_thenReloadsIcon() = runTest {
        // Given
        val first = assertIs<SuccessResult>(imageLoader.execute(request(packageInfo(versionCode = 1))))
        val updatedRequest = request(packageInfo(versionCode = 2))

        // When
        val updated = imageLoader.execute(updatedRequest)

        // Then
        assertIs<SuccessResult>(updated)
        assertNotEquals(first.memoryCacheKey, updated.memoryCacheKey)
        assertEquals(DataSource.DISK, updated.dataSource)
    }

    @Test
    fun givenMissingApplicationInfo_whenLoadingIcon_thenReturnsError() = runTest {
        // Given
        val request = request(PackageInfo())

        // When
        val result = imageLoader.execute(request)

        // Then
        assertIs<ErrorResult>(result)
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(versionCode: Int = 1) = PackageInfo().apply {
        packageName = context.packageName
        applicationInfo = context.applicationInfo
        this.versionCode = versionCode
    }

    private fun request(packageInfo: PackageInfo) = ImageRequest.Builder(context)
        .data(packageInfo)
        .size(ICON_SIZE)
        .build()

    private companion object {
        const val ICON_SIZE = 48
    }
}
