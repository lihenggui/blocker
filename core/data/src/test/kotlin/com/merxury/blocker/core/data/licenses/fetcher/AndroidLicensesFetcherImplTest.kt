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

package com.merxury.blocker.core.data.licenses.fetcher

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test

@OptIn(ExperimentalSerializationApi::class)
@ExperimentalCoroutinesApi
class AndroidLicensesFetcherImplTest {

    private val assetManager: AssetManager = mock(AssetManager::class.java)
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val ioDispatcher = StandardTestDispatcher()

    private lateinit var fetcher: AndroidLicensesFetcherImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(ioDispatcher)
        fetcher = AndroidLicensesFetcherImpl(assetManager, json, ioDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenValidLicensesJson_whenFetch_thenReturnsListOfLicenseItems() = runTest {
        val licensesJson = """
            [
                {
                    "groupId": "androidx.activity",
                    "artifactId": "activity",
                    "version": "1.9.1",
                    "name": "Activity",
                    "spdxLicenses": [
                        {
                            "identifier": "Apache-2.0",
                            "name": "Apache License 2.0",
                            "url": "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    ],
                    "scm": {
                        "url": "https://cs.android.com/androidx/platform/frameworks/support"
                    }
                },
                {
                    "groupId": "androidx.activity",
                    "artifactId": "activity-compose",
                    "version": "1.9.1",
                    "name": "Activity Compose",
                    "spdxLicenses": [
                        {
                            "identifier": "Apache-2.0",
                            "name": "Apache License 2.0",
                            "url": "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    ],
                    "scm": {
                        "url": "https://cs.android.com/androidx/platform/frameworks/support"
                    }
                }
            ]
        """.trimIndent()
        val inputStream = ByteArrayInputStream(licensesJson.toByteArray())
        `when`(assetManager.open("licenses.json")).thenReturn(inputStream)

        val result = fetcher.fetch()

        assert(result.size == 2)
        assert(result[0].name == "Activity")
        assert(result[1].name == "Activity Compose")
    }

    @Test
    fun givenIOException_whenFetch_thenReturnsEmptyList() = runTest {
        `when`(assetManager.open("licenses.json")).thenThrow(IOException::class.java)

        val result = fetcher.fetch()

        assert(result.isEmpty())
    }

    @Test
    fun givenFilesInAssetsDirectory_whenLogAssetFiles_thenLogsFiles() {
        val files = arrayOf("file1.txt", "file2.txt")
        `when`(assetManager.list("")).thenReturn(files)

        fetcher.logAssetFiles()

        verify(assetManager).list("")
    }
}