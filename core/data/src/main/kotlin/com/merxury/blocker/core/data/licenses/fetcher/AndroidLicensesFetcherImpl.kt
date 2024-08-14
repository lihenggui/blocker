// Copyright 2023, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.licenses.fetcher

import android.app.Application
import com.merxury.blocker.core.model.licenses.LicenseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

class AndroidLicensesFetcherImpl @Inject constructor(private val context: Application) : LicensesFetcher {
    @ExperimentalSerializationApi
    override suspend fun fetch(): List<LicenseItem> {
        var licenseItemList: List<LicenseItem> = emptyList()
        withContext(Dispatchers.IO) {
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
