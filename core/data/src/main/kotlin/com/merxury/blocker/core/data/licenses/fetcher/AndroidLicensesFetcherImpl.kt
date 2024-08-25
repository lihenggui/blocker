// Copyright 2023, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.licenses.fetcher

import android.app.Application
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.licenses.LicenseItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

internal class AndroidLicensesFetcherImpl @Inject constructor(
    private val context: Application,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : LicensesFetcher {
    @ExperimentalSerializationApi
    override suspend fun fetch(): List<LicenseItem> {
        var licenseItemList: List<LicenseItem> = emptyList()
        withContext(ioDispatcher) {
            try {
                val inputStream: InputStream = context.assets.open("artifacts.json")
                val json = Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
                licenseItemList = json.decodeFromStream(inputStream)
            } catch (ex: IOException) {
                licenseItemList = emptyList()
            }
        }
        return licenseItemList
    }
}
