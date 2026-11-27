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

package com.merxury.blocker.coil

import android.app.Application
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.merxury.blocker.core.ui.coil.ApkIcon
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ApkIconFetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUp() {
        imageLoader = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }
            .components {
                add(ApkIconKeyer())
                add(ApkIconFetcher.Factory(ICON_SIZE, true, context))
            }
            .build()
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun givenAppOfOwnUser_whenLoadingIcon_thenReturnsConfiguredSize() = runTest {
        val result = imageLoader.execute(request(ownIcon()))

        assertIs<SuccessResult>(result)
        assertEquals(ICON_SIZE, result.image.width)
        assertEquals(DataSource.DISK, result.dataSource)
    }

    @Test
    fun givenSameApp_whenKeyedForAnotherUserOrVersion_thenKeysDiffer() {
        val keyer = ApkIconKeyer()
        val options = Options(context)
        val own = keyer.key(ownIcon(), options)

        assertNotEquals(own, keyer.key(ownIcon().copy(uid = Process.myUid() + 1_000_000), options))
        assertNotEquals(own, keyer.key(ownIcon().copy(versionCode = 2), options))
        assertEquals(own, keyer.key(ownIcon(), options))
    }

    @Test
    fun givenCachedIcon_whenLoadingSameApp_thenUsesMemoryCache() = runTest {
        val first = assertIs<SuccessResult>(imageLoader.execute(request(ownIcon())))

        val second = assertIs<SuccessResult>(imageLoader.execute(request(ownIcon())))

        assertEquals(first.memoryCacheKey, second.memoryCacheKey)
        assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
    }

    @Test
    fun givenAppNotInstalledAndNoApk_whenLoadingIcon_thenReturnsError() = runTest {
        val result = imageLoader.execute(request(ApkIcon("com.example.missing", apkPath = null, versionCode = 1, uid = null)))

        assertIs<ErrorResult>(result)
    }

    @Test
    fun givenUnreadableApk_whenLoadingIconOfAnotherUser_thenReturnsError() = runTest {
        val icon = ApkIcon("com.example.missing", apkPath = "/nonexistent/base.apk", versionCode = 1, uid = Process.myUid() + 1_000_000)

        val result = imageLoader.execute(request(icon))

        assertIs<ErrorResult>(result)
    }

    private fun ownIcon() = ApkIcon(context.packageName, apkPath = null, versionCode = 1, uid = Process.myUid())

    private fun request(icon: ApkIcon) = ImageRequest.Builder(context)
        .data(icon)
        .size(ICON_SIZE)
        .build()

    private companion object {
        const val ICON_SIZE = 48
    }
}
