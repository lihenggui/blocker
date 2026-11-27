// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.generalrule

import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.data.repository.catalog.resolveCatalogText
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.data.repository.userdata.observeLibDisplayLanguage
import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.database.catalog.CatalogRuleTextEntity
import com.merxury.blocker.core.database.catalog.CatalogRuleWithText
import com.merxury.blocker.core.model.data.GeneralRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

internal class CatalogGeneralRuleRepository @Inject constructor(
    private val dao: CatalogDao,
    private val catalog: CatalogRepository,
    private val userData: UserDataRepository,
) : GeneralRuleRepository {

    override fun getGeneralRules(): Flow<List<GeneralRule>> = rulesFrom { dao.readRules() }

    override fun getGeneralRule(id: Int): Flow<GeneralRule> = rulesFrom {
        listOfNotNull(dao.readRule(id.toLong()))
    }.mapNotNull { it.singleOrNull() }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> = rulesFrom { language ->
        dao.searchRules(language, keyword)
    }

    private fun rulesFrom(read: suspend (String) -> List<CatalogRuleWithText>): Flow<List<GeneralRule>> = combine(
        catalog.catalogState,
        userData.observeLibDisplayLanguage(),
    ) { _, language ->
        val (sequence, rules) = dao.readRuleSnapshot { read(language) } ?: return@combine emptyList()
        rules.map { it.toGeneralRule(language, sequence) }
    }

    private suspend fun CatalogRuleWithText.toGeneralRule(language: String, sequence: Long): GeneralRule {
        val selected = texts.firstOrNull { it.language == language }
        val english = texts.first { it.language == "en" }
        return GeneralRule(
            id = rule.id.toInt(),
            name = checkNotNull(resolveCatalogText(selected, english, CatalogRuleTextEntity::name)),
            iconUrl = rule.icon?.let { catalog.iconFile(it, sequence)?.absolutePath },
            company = resolveCatalogText(selected, english, CatalogRuleTextEntity::company),
            searchKeyword = rule.keywords,
            description = resolveCatalogText(selected, english, CatalogRuleTextEntity::description),
            safeToBlock = rule.safeToBlock,
            sideEffect = resolveCatalogText(selected, english, CatalogRuleTextEntity::sideEffect),
            website = rule.website,
            contributors = rule.contributors,
        )
    }
}
