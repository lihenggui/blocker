// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.componentdetail.datasource

import com.merxury.blocker.core.data.repository.catalog.resolveCatalogText
import com.merxury.blocker.core.database.catalog.CatalogComponentTextEntity
import com.merxury.blocker.core.database.catalog.CatalogComponentWithText
import com.merxury.blocker.core.database.catalog.CatalogDao
import com.merxury.blocker.core.database.catalog.CatalogRuleTextEntity
import com.merxury.blocker.core.model.data.ComponentDetail
import javax.inject.Inject
import kotlin.time.Instant

internal class CatalogComponentDetailDataSource @Inject constructor(private val dao: CatalogDao) {
    suspend fun findByNames(names: Collection<String>, language: String): List<ComponentDetail> = dao.findComponents(names).map { it.toComponentDetail(language) }

    private fun CatalogComponentWithText.toComponentDetail(language: String): ComponentDetail {
        val selected = texts.firstOrNull { it.language == language }
        val english = texts.firstOrNull { it.language == "en" }
        val selectedSdk = sdkTexts.firstOrNull { it.language == language }
        val englishSdk = sdkTexts.firstOrNull { it.language == "en" }
        return ComponentDetail(
            name = component.name,
            sdkName = resolveCatalogText(selectedSdk, englishSdk, CatalogRuleTextEntity::name),
            description = resolveCatalogText(selected, english, CatalogComponentTextEntity::description),
            disableEffect = resolveCatalogText(selected, english, CatalogComponentTextEntity::disableEffect),
            contributor = component.contributor,
            recommendToBlock = component.recommendToBlock,
            lastUpdateTime = component.updatedAt?.let(Instant::parse),
        )
    }
}
