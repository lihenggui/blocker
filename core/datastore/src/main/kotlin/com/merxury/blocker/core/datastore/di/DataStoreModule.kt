/*
 * Copyright 2022 The Android Open Source Project
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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.migrations.SharedPreferencesMigration
import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.datastore.AppPropertiesSerializer
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.UserPreferencesSerializer
import com.merxury.blocker.core.datastore.copy
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    internal fun providesUserPreferencesDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        userPreferencesSerializer: UserPreferencesSerializer,
    ): DataStore<UserPreferences> = createUserPreferencesDataStore(
        serializer = userPreferencesSerializer,
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
    ) {
        context.dataStoreFile("user_preferences.pb")
    }

    @Provides
    @Singleton
    internal fun providesAppPropertiesDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        appPropertiesSerializer: AppPropertiesSerializer,
    ): DataStore<AppProperties> = createAppPropertiesDataStore(
        serializer = appPropertiesSerializer,
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        migrations = listOf(catalogSyncMigration(context)),
    ) {
        context.dataStoreFile("app_properties.pb")
    }
}

private const val LEGACY_SYNC_TIMESTAMP = "last_synced_time"

private fun catalogSyncMigration(context: Context) = SharedPreferencesMigration<AppProperties>(
    context = context,
    sharedPreferencesName = "sync_rule",
    keysToMigrate = setOf(LEGACY_SYNC_TIMESTAMP),
) { preferences, current ->
    if (current.lastCatalogSyncedEpochMs != 0L) {
        current
    } else {
        current.copy { lastCatalogSyncedEpochMs = preferences.getLong(LEGACY_SYNC_TIMESTAMP, 0) }
    }
}
