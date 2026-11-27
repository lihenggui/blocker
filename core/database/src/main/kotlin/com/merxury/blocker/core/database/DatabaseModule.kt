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

package com.merxury.blocker.core.database

import android.content.Context
import androidx.room.Room
import com.merxury.blocker.core.database.cache.COMPONENT_CACHE_DATABASE_NAME
import com.merxury.blocker.core.database.cache.ComponentCacheDatabase
import com.merxury.blocker.core.database.catalog.CATALOG_DATABASE_NAME
import com.merxury.blocker.core.database.catalog.CatalogDatabase
import com.merxury.blocker.core.database.history.OPERATION_HISTORY_DATABASE_NAME
import com.merxury.blocker.core.database.history.OPERATION_HISTORY_MIGRATIONS
import com.merxury.blocker.core.database.history.OperationHistoryDatabase
import com.merxury.blocker.core.database.record.BLOCKER_RECORDS_DATABASE_NAME
import com.merxury.blocker.core.database.record.BLOCKER_RECORDS_MIGRATIONS
import com.merxury.blocker.core.database.record.BlockerRecordsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideCatalogDatabase(@ApplicationContext context: Context): CatalogDatabase = Room.databaseBuilder(
        context,
        CatalogDatabase::class.java,
        CATALOG_DATABASE_NAME,
    )
        // The catalog is rebuildable. Startup work reinstalls the bundled seed after schema resets.
        .fallbackToDestructiveMigration(true)
        .build()

    @Provides
    @Singleton
    fun provideComponentCacheDatabase(
        @ApplicationContext context: Context,
    ): ComponentCacheDatabase = Room.databaseBuilder(
        context,
        ComponentCacheDatabase::class.java,
        COMPONENT_CACHE_DATABASE_NAME,
    )
        // The cache only mirrors system state and is rebuilt from the system, so dropping it on a
        // schema change loses nothing.
        .fallbackToDestructiveMigration(true)
        .build()

    @Provides
    @Singleton
    fun provideBlockerRecordsDatabase(
        @ApplicationContext context: Context,
    ): BlockerRecordsDatabase = Room.databaseBuilder(
        context,
        BlockerRecordsDatabase::class.java,
        BLOCKER_RECORDS_DATABASE_NAME,
    )
        // User data that cannot be rebuilt: a schema change without a migration must fail, never
        // drop the records.
        .addMigrations(*BLOCKER_RECORDS_MIGRATIONS)
        .build()

    @Provides
    @Singleton
    fun provideOperationHistoryDatabase(
        @ApplicationContext context: Context,
    ): OperationHistoryDatabase = Room.databaseBuilder(
        context,
        OperationHistoryDatabase::class.java,
        OPERATION_HISTORY_DATABASE_NAME,
    )
        .addMigrations(*OPERATION_HISTORY_MIGRATIONS)
        .build()
}
