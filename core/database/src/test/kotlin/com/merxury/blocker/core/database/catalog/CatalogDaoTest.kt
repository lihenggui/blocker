// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class CatalogDaoTest {
    private lateinit var database: CatalogDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CatalogDatabase::class.java,
        ).build()
        dao = database.catalogDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun givenMoreNamesThanFitInOneQuery_whenFindingComponents_thenEveryExactMatchIsReturnedOnce() = runTest {
        val names = List(1_005) { "com.example.Example$it" }
        dao.insertComponents((names + "com.example.Other").map { CatalogComponentEntity(it, null, true, null, null) })
        val texts = names.map { CatalogComponentTextEntity(it, "en", it, null) }
        dao.insertComponentTexts(texts)

        val results = dao.findComponents(names + listOf(names.first(), "com.example.%", "com.example.Example", "COM.EXAMPLE.EXAMPLE0"))

        assertThat(results.map { it.component.name }).containsExactlyElementsIn(names)
        assertThat(results.flatMap { it.texts }).containsExactlyElementsIn(texts)
        assertThat(dao.findComponents(emptyList())).isEmpty()
    }

    @Test
    fun givenLocalizedRulesAndComponents_whenReadingThem_thenEveryLanguageAndKeywordOrderArePreserved() = runTest {
        val rule = CatalogRuleEntity(1, null, null, listOf("second", "first"), null, listOf("author"))
        val ruleTexts = listOf(
            CatalogRuleTextEntity(1, "en", "English", null, "English description", null),
            CatalogRuleTextEntity(1, "zh-CN", null, "", null, "Chinese side effect"),
        )
        val component = CatalogComponentEntity("com.example.Component", 1, false, null, "2026-01-01T00:00:00.000Z")
        val componentTexts = listOf(
            CatalogComponentTextEntity(component.name, "en", "", null),
            CatalogComponentTextEntity(component.name, "zh-CN", null, "Chinese disable effect"),
        )
        dao.writeTransaction {
            dao.insertRules(listOf(rule))
            dao.insertRuleTexts(ruleTexts)
            dao.insertComponents(listOf(component))
            dao.insertComponentTexts(componentTexts)
        }

        val storedRule = dao.readRules().single()
        assertThat(storedRule.rule).isEqualTo(rule)
        assertThat(storedRule.rule.keywords).containsExactly("second", "first").inOrder()
        assertThat(storedRule.texts).containsExactlyElementsIn(ruleTexts)
        val storedComponent = dao.findComponents(listOf(component.name)).single()
        assertThat(storedComponent.component).isEqualTo(component)
        assertThat(storedComponent.texts).containsExactlyElementsIn(componentTexts)
    }
}
