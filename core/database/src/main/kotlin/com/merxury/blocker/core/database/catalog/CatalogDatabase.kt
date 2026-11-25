// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.catalog

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.core.database.util.ListConverter

internal const val CATALOG_DATABASE_NAME = "catalog.db"

@Database(
    entities = [
        CatalogRuleEntity::class,
        CatalogRuleTextEntity::class,
        CatalogComponentEntity::class,
        CatalogComponentTextEntity::class,
        CatalogStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ListConverter::class)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
