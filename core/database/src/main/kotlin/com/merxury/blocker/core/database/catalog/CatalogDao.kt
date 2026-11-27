// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.merxury.blocker.core.database.util.escapeLikeWildcards
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogDao {
    @Query("SELECT * FROM catalog_state LIMIT 1")
    abstract fun observeState(): Flow<CatalogStateEntity?>

    @Query("SELECT * FROM catalog_state LIMIT 1")
    abstract suspend fun readState(): CatalogStateEntity?

    @Transaction
    @Query("SELECT * FROM catalog_rule ORDER BY id")
    abstract suspend fun readRules(): List<CatalogRuleWithText>

    @Transaction
    @Query("SELECT * FROM catalog_rule WHERE id = :id")
    abstract suspend fun readRule(id: Long): CatalogRuleWithText?

    suspend fun searchRules(language: String, keyword: String): List<CatalogRuleWithText> = querySearchRules(language, keyword.escapeLikeWildcards())

    @Transaction
    @Query(
        """
        SELECT rule.* FROM catalog_rule AS rule
        LEFT JOIN catalog_rule_text AS localized ON localized.rule_id = rule.id AND localized.language = :language
        JOIN catalog_rule_text AS english ON english.rule_id = rule.id AND english.language = 'en'
        WHERE COALESCE(localized.name, english.name) LIKE '%' || :keyword || '%' ESCAPE '\'
        ORDER BY rule.id
        """,
    )
    protected abstract suspend fun querySearchRules(language: String, keyword: String): List<CatalogRuleWithText>

    /** Reads rule rows with the sequence that owns their icon files. */
    @Transaction
    open suspend fun <T> readRuleSnapshot(read: suspend () -> T): Pair<Long, T>? {
        val sequence = readState()?.sequence ?: return null
        return sequence to read()
    }

    @Transaction
    open suspend fun findComponents(names: Collection<String>): List<CatalogComponentWithText> = names.distinct()
        .chunked(NAME_CHUNK_SIZE)
        .flatMap { findComponentBatch(it) }

    @Transaction
    @Query("SELECT * FROM catalog_component WHERE name IN (:names)")
    protected abstract suspend fun findComponentBatch(names: List<String>): List<CatalogComponentWithText>

    @Insert
    abstract suspend fun insertRules(rules: List<CatalogRuleEntity>)

    @Insert
    abstract suspend fun insertRuleTexts(texts: List<CatalogRuleTextEntity>)

    @Insert
    abstract suspend fun insertComponents(components: List<CatalogComponentEntity>)

    @Insert
    abstract suspend fun insertComponentTexts(texts: List<CatalogComponentTextEntity>)

    @Insert
    abstract suspend fun insertState(state: CatalogStateEntity)

    @Transaction
    open suspend fun clearAll() {
        clearComponentTexts()
        clearComponents()
        clearRuleTexts()
        clearRules()
        clearState()
    }

    @Transaction
    open suspend fun <T> writeTransaction(block: suspend () -> T): T = block()

    @Query("DELETE FROM catalog_component_text")
    protected abstract suspend fun clearComponentTexts()

    @Query("DELETE FROM catalog_component")
    protected abstract suspend fun clearComponents()

    @Query("DELETE FROM catalog_rule_text")
    protected abstract suspend fun clearRuleTexts()

    @Query("DELETE FROM catalog_rule")
    protected abstract suspend fun clearRules()

    @Query("DELETE FROM catalog_state")
    protected abstract suspend fun clearState()

    private companion object {
        // Older Android SQLite builds accept at most 999 host parameters per statement.
        const val NAME_CHUNK_SIZE = 900
    }
}
