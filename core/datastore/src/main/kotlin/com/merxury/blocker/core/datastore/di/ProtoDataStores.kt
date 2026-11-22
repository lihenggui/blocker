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

package com.merxury.blocker.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.datastore.AppPropertiesSerializer
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.UserPreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.io.File

/**
 * Builds the user preferences store. Shared by [DataStoreModule] and tests.
 */
internal fun createUserPreferencesDataStore(
    serializer: UserPreferencesSerializer,
    scope: CoroutineScope,
    produceFile: () -> File,
): DataStore<UserPreferences> = DataStoreFactory.create(
    serializer = serializer,
    corruptionHandler = replaceCorruptFileWithDefault(
        storeName = "user preferences",
        defaultValue = serializer.defaultValue,
    ),
    scope = scope,
    produceFile = produceFile,
)

/**
 * Replaces an unreadable file with [defaultValue] so reads and writes keep working.
 * The previous contents are lost, so the corruption is logged with the [storeName].
 */
private fun <T> replaceCorruptFileWithDefault(
    storeName: String,
    defaultValue: T,
): ReplaceFileCorruptionHandler<T> = ReplaceFileCorruptionHandler { exception ->
    Timber.e(exception, "The %s DataStore file is corrupt, resetting it to defaults", storeName)
    defaultValue
}

/**
 * Builds the app properties store. Shared by [DataStoreModule] and tests.
 */
internal fun createAppPropertiesDataStore(
    serializer: AppPropertiesSerializer,
    scope: CoroutineScope,
    produceFile: () -> File,
): DataStore<AppProperties> = DataStoreFactory.create(
    serializer = serializer,
    corruptionHandler = replaceCorruptFileWithDefault(
        storeName = "app properties",
        defaultValue = serializer.defaultValue,
    ),
    scope = scope,
    produceFile = produceFile,
)
