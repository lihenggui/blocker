// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "WakeBlock", indices = [Index("target")])
data class WakeBlockEntity(
    @PrimaryKey val pathKey: String,
    val target: String,
    val method: String,
    val componentType: String?,
    val sender: String?,
    val component: String?,
    val action: String?,
    val blockedAt: Long,
)

@Dao
interface WakeBlockDao {
    @Query("SELECT * FROM WakeBlock ORDER BY pathKey")
    fun observeAll(): Flow<List<WakeBlockEntity>>

    @Query("SELECT * FROM WakeBlock ORDER BY pathKey")
    suspend fun all(): List<WakeBlockEntity>

    @Upsert
    suspend fun upsert(blocks: List<WakeBlockEntity>)

    @Query("DELETE FROM WakeBlock WHERE pathKey IN (:pathKeys)")
    suspend fun delete(pathKeys: List<String>)

    @Query("DELETE FROM WakeBlock WHERE method = 'FIREWALL_RULE' AND (:target IS NULL OR target = :target)")
    suspend fun deleteFirewall(target: String?)

    @Transaction
    suspend fun update(added: List<WakeBlockEntity>, removed: List<String>) {
        // Bound bind parameters on Android releases with SQLite's 999-variable limit.
        removed.chunked(999).forEach { delete(it) }
        if (added.isNotEmpty()) upsert(added)
    }
}
