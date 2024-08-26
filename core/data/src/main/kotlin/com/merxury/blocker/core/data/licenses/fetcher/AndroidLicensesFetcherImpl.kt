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
import androidx.annotation.VisibleForTesting
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.licenses.LicenseItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

internal class AndroidLicensesFetcherImpl @Inject constructor(
    private val assetManager: AssetManager,
    private val json: Json,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : LicensesFetcher {

    @ExperimentalSerializationApi
    override suspend fun fetch(): List<LicenseItem> = withContext(ioDispatcher) {
        try {
            assetManager.open("licenses.json").use { inputStream ->
                val licenseItemList: List<LicenseItem> = json.decodeFromStream(inputStream)
                Timber.d( "Fetched ${licenseItemList.size} licenses" )
                return@withContext licenseItemList
            }
        } catch (ex: IOException) {
            Timber.e(ex, "Failed to fetch licenses")
            logAssetFiles()
            return@withContext emptyList()
        }
    }

    @VisibleForTesting
    fun logAssetFiles() {
        try {
            val files = assetManager.list("")?.joinToString()
            Timber.d( "Files in assets directory: $files" )
        } catch (ex: IOException) {
            Timber.e(ex, "Failed to list files in assets directory")
        }
    }
}
