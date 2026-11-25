// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.catalog

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "catalog_rule")
data class CatalogRuleEntity(
    @PrimaryKey val id: Long,
    val icon: String?,
    val website: String?,
    val keywords: List<String>,
    @ColumnInfo(name = "safe_to_block") val safeToBlock: Boolean?,
    val contributors: List<String>,
)

@Entity(
    tableName = "catalog_rule_text",
    primaryKeys = ["rule_id", "language"],
    foreignKeys = [
        ForeignKey(entity = CatalogRuleEntity::class, parentColumns = ["id"], childColumns = ["rule_id"]),
    ],
)
data class CatalogRuleTextEntity(
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    val language: String,
    val name: String?,
    val company: String?,
    val description: String?,
    @ColumnInfo(name = "side_effect") val sideEffect: String?,
)

data class CatalogRuleWithText(
    @Embedded val rule: CatalogRuleEntity,
    @Relation(parentColumn = "id", entityColumn = "rule_id") val texts: List<CatalogRuleTextEntity>,
)

@Entity(
    tableName = "catalog_component",
    foreignKeys = [
        ForeignKey(entity = CatalogRuleEntity::class, parentColumns = ["id"], childColumns = ["sdk_rule_id"]),
    ],
    indices = [Index("sdk_rule_id")],
)
data class CatalogComponentEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "sdk_rule_id") val sdkRuleId: Long?,
    @ColumnInfo(name = "recommend_to_block") val recommendToBlock: Boolean,
    val contributor: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
)

@Entity(
    tableName = "catalog_component_text",
    primaryKeys = ["name", "language"],
    foreignKeys = [
        ForeignKey(entity = CatalogComponentEntity::class, parentColumns = ["name"], childColumns = ["name"]),
    ],
)
data class CatalogComponentTextEntity(
    val name: String,
    val language: String,
    val description: String?,
    @ColumnInfo(name = "disable_effect") val disableEffect: String?,
)

data class CatalogComponentWithText(
    @Embedded val component: CatalogComponentEntity,
    @Relation(parentColumn = "name", entityColumn = "name") val texts: List<CatalogComponentTextEntity>,
)

@Entity(tableName = "catalog_state")
// The installer only accepts format 2, so its format is also the singleton key.
data class CatalogStateEntity(
    @PrimaryKey val format: Int,
    val sequence: Long,
    @ColumnInfo(name = "source_commit") val sourceCommit: String,
    @ColumnInfo(name = "published_at") val publishedAt: String,
)
