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

package com.merxury.blocker.di

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoilModuleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var imageLoader: ImageLoader
    private var networkFactoryAccesses = 0
    private val networkError = IllegalStateException("No network client is available in this test")

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun givenLazyNetworkClient_whenCreatingImageLoader_thenDoesNotInitializeNetworkClient() {
        // Given
        val callFactory = dagger.Lazy<okhttp3.Call.Factory> {
            networkFactoryAccesses++
            throw networkError
        }

        // When
        imageLoader = CoilModule.imageLoader(callFactory, context)

        // Then
        assertEquals(0, networkFactoryAccesses)
    }

    @Test
    fun givenInjectedCallFactory_whenLoadingNetworkImage_thenUsesInjectedFactory() = runTest {
        // Given
        imageLoader = createImageLoader()
        val request = ImageRequest.Builder(context)
            .data("https://example.com/icon.png")
            .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
            .build()

        // When
        val result = imageLoader.execute(request)

        // Then
        assertIs<ErrorResult>(result)
        assertIs<IllegalStateException>(result.throwable)
        assertEquals(networkError.message, result.throwable.message)
        assertEquals(1, networkFactoryAccesses)
    }

    @Test
    fun givenCachedLocalIcon_whenLoadingUnchangedFile_thenUsesMemoryCache() = runTest {
        // Given
        imageLoader = createImageLoader()
        val file = temporaryFolder.newFile("icon.png")
        writeBitmap(file, Color.RED)
        val first = assertIs<SuccessResult>(imageLoader.execute(request(file)))

        // When
        val cached = imageLoader.execute(request(file))

        // Then
        assertIs<SuccessResult>(cached)
        assertEquals(first.memoryCacheKey, cached.memoryCacheKey)
        assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
        assertEquals(Color.RED, cached.image.toBitmap().getPixel(0, 0))
        assertEquals(0, networkFactoryAccesses)
    }

    @Test
    fun givenCachedLocalIcon_whenLoadingChangedFile_thenReloadsUpdatedImage() = runTest {
        // Given
        imageLoader = createImageLoader()
        val file = temporaryFolder.newFile("icon.png")
        writeBitmap(file, Color.RED)
        assertTrue(file.setLastModified(1000))
        val first = assertIs<SuccessResult>(imageLoader.execute(request(file)))
        writeBitmap(file, Color.BLUE)
        assertTrue(file.setLastModified(2000))

        // When
        val updated = imageLoader.execute(request(file))

        // Then
        assertIs<SuccessResult>(updated)
        assertNotEquals(first.memoryCacheKey, updated.memoryCacheKey)
        assertEquals(DataSource.DISK, updated.dataSource)
        assertEquals(Color.BLUE, updated.image.toBitmap().getPixel(0, 0))
        assertEquals(0, networkFactoryAccesses)
    }

    @Test
    fun givenLocalSvg_whenLoadingImage_thenDecodesWithoutInitializingNetworkClient() = runTest {
        // Given
        imageLoader = createImageLoader()
        val file = temporaryFolder.newFile("icon.svg").apply {
            writeText(
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                    <rect width="16" height="16" fill="#ff0000"/>
                </svg>
                """.trimIndent(),
            )
        }

        // When
        val result = imageLoader.execute(request(file))

        // Then
        assertIs<SuccessResult>(result)
        assertEquals(Color.RED, result.image.toBitmap().getPixel(8, 8))
        assertEquals(0, networkFactoryAccesses)
    }

    private fun createImageLoader() = CoilModule.imageLoader(
        okHttpCallFactory = dagger.Lazy {
            networkFactoryAccesses++
            throw networkError
        },
        application = context,
    )

    private fun request(file: File) = ImageRequest.Builder(context)
        .data(file)
        .size(16)
        .allowHardware(false)
        .build()

    private fun writeBitmap(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
