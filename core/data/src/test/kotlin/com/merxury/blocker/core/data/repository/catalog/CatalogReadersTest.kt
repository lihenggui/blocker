// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.repository.componentdetail.LocalComponentDetailRepository
import com.merxury.blocker.core.data.repository.componentdetail.datasource.CatalogComponentDetailDataSource
import com.merxury.blocker.core.data.repository.componentdetail.datasource.UserGeneratedComponentDetailDataSource
import com.merxury.blocker.core.data.repository.generalrule.CatalogGeneralRuleRepository
import com.merxury.blocker.core.database.catalog.CatalogDatabase
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogReadersTest {
    @get:Rule val folder = TemporaryFolder()

    private val userData = TestUserDataRepository().apply { sendUserData(defaultUserData.copy(libDisplayLanguage = "en")) }
    private lateinit var database: CatalogDatabase
    private lateinit var installer: CatalogInstaller
    private lateinit var rules: CatalogGeneralRuleRepository
    private lateinit var details: LocalComponentDetailRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        val dao = database.catalogDao()
        val icons = CatalogIcons(folder.root)
        installer = CatalogInstaller(dao, icons, Dispatchers.IO)
        val catalog = LocalCatalogRepository(
            dao,
            installer,
            BundledCatalogSource(context),
            icons,
            Dispatchers.IO,
            CatalogDownloader(folder.root, dao, installer, Dispatchers.IO) { _, _ ->
                error("Unexpected catalog download")
            },
        )
        rules = CatalogGeneralRuleRepository(dao, catalog, userData)
        val userDetails = UserGeneratedComponentDetailDataSource(
            userData,
            FakeComponentStatusRepository(),
            folder.root,
            "generated",
            Json,
            Dispatchers.IO,
        )
        details = LocalComponentDetailRepository(CatalogComponentDetailDataSource(dao), userDetails, catalog, userData, Dispatchers.IO)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun givenChineseTextWithMissingAndEmptyFields_whenReadingRules_thenEachFieldResolvesIndependently() = runTest {
        install()
        userData.setLibDisplayLanguage("zh-CN")

        val rule = rules.getGeneralRule(1).first()

        assertThat(rule).isEqualTo(
            GeneralRule(
                id = 1,
                name = "示例 SDK",
                iconUrl = folder.root.resolve("catalog/icon-1/$FIXTURE_ICON").absolutePath,
                company = "Company",
                searchKeyword = listOf("second", "first"),
                description = "",
                safeToBlock = true,
                sideEffect = "Localized effect",
                website = "https://example.com",
                contributors = listOf("One", "Two"),
            ),
        )
        val withoutIcon = rules.getGeneralRule(2).first()
        assertThat(withoutIcon.name).isEqualTo("Second SDK")
        assertThat(withoutIcon.company).isNull()
        assertThat(withoutIcon.iconUrl).isNull()
    }

    @Test
    fun givenLocalizedRules_whenSearching_thenOnlyResolvedNamesUseSqlLike() = runTest {
        install()
        userData.setLibDisplayLanguage("zh-CN")

        assertThat(rules.searchGeneralRule("示例 s_k").first().map { it.id }).containsExactly(1)
        assertThat(rules.searchGeneralRule("sec%SDK").first().map { it.id }).containsExactly(2)
        assertThat(rules.searchGeneralRule("Example").first()).isEmpty()
        assertThat(rules.searchGeneralRule("Company").first()).isEmpty()
        assertThat(rules.searchGeneralRule("first").first()).isEmpty()
        assertThat(rules.searchGeneralRule("").first().map { it.id }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun givenOpenRuleFlows_whenTheLanguageChanges_thenListDetailAndSearchEmitAgain() = runTest {
        install()
        userData.setLibDisplayLanguage("en")
        turbineScope {
            val all = rules.getGeneralRules().testIn(backgroundScope)
            val detail = rules.getGeneralRule(1).testIn(backgroundScope)
            val search = rules.searchGeneralRule("Example").testIn(backgroundScope)
            assertThat(all.awaitItem().map { it.name }).containsExactly("Example SDK", "Second SDK").inOrder()
            assertThat(detail.awaitItem().description).isEqualTo("English description")
            assertThat(search.awaitItem().map { it.id }).containsExactly(1)

            userData.setLibDisplayLanguage("zh-CN")

            assertThat(all.awaitItem().map { it.name }).containsExactly("示例 SDK", "Second SDK").inOrder()
            assertThat(detail.awaitItem().description).isEmpty()
            assertThat(search.awaitItem()).isEmpty()
            all.cancelAndIgnoreRemainingEvents()
            detail.cancelAndIgnoreRemainingEvents()
            search.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenOpenRuleFlows_whenInstallingANewerCatalog_thenEveryFlowUsesTheNewIconPath() = runTest {
        install()
        turbineScope {
            val all = rules.getGeneralRules().testIn(backgroundScope)
            val detail = rules.getGeneralRule(1).testIn(backgroundScope)
            val search = rules.searchGeneralRule("Example").testIn(backgroundScope)
            val oldPath = checkNotNull(detail.awaitItem().iconUrl)
            assertThat(all.awaitItem().first().iconUrl).isEqualTo(oldPath)
            assertThat(search.awaitItem().single().iconUrl).isEqualTo(oldPath)

            install(sequence = 2, icon = "new icon")

            val newPath = checkNotNull(detail.awaitItem().iconUrl)
            assertThat(all.awaitItem().first().iconUrl).isEqualTo(newPath)
            assertThat(search.awaitItem().single().iconUrl).isEqualTo(newPath)
            assertThat(newPath).isNotEqualTo(oldPath)
            assertThat(File(newPath).readText()).isEqualTo("new icon")
            assertThat(File(oldPath).readText()).isEqualTo("old icon")
            all.cancelAndIgnoreRemainingEvents()
            detail.cancelAndIgnoreRemainingEvents()
            search.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnOpenComponentLookup_whenTheCatalogAndLanguageChange_thenDetailsFollowBoth() = runTest {
        userData.setLibDisplayLanguage("zh-CN")
        details.getLocalComponentDetail(FIXTURE_COMPONENT_NAME).test {
            assertThat(awaitItem()).isNull()

            install()
            assertThat(awaitItem()).isEqualTo(
                ComponentDetail(
                    name = FIXTURE_COMPONENT_NAME,
                    sdkName = "示例 SDK",
                    description = "",
                    disableEffect = "English effect",
                    contributor = "Author",
                    recommendToBlock = true,
                    lastUpdateTime = Instant.parse("2026-10-20T00:00:00Z"),
                ),
            )

            userData.setLibDisplayLanguage("en")
            val english = checkNotNull(awaitItem())
            assertThat(english.sdkName).isEqualTo("Example SDK")
            assertThat(english.description).isEqualTo("English component")

            install(sequence = 2, components = LOCALIZED_COMPONENT.replace("English component", "Updated component"))
            assertThat(awaitItem()?.description).isEqualTo("Updated component")
        }
    }

    @Test
    fun givenNamesAcrossQueryChunks_whenReadingDetails_thenMatchesAreExactAndResolveTheLinkedSdk() = runTest {
        val names = (0..900).map { "com.example.Entry" + it.toString().padStart(4, '0') }
        val components = names.joinToString("\n") { name ->
            """{"name":"$name","sdkRuleId":2,"recommendToBlock":false,"i18n":{"en":{"description":"$name"}}}"""
        }
        install(components = components)
        userData.setLibDisplayLanguage("zh-CN")

        val result = details.getLocalComponentDetails((names + listOf("com.example.Entry", names.first().lowercase())).toSet()).first()

        assertThat(result.keys).containsExactlyElementsIn(names)
        assertThat(result[names.last()]?.description).isEqualTo(names.last())
        assertThat(result[names.last()]?.sdkName).isEqualTo("Second SDK")
    }

    @Test
    fun givenAComponentInTheCatalog_whenTheUserSavesAnEmptyDescription_thenItTakesPrecedence() = runTest {
        install()
        userData.setLibDisplayLanguage("en")
        val name = FIXTURE_COMPONENT_NAME

        details.getLocalComponentDetails(setOf(name)).test {
            assertThat(awaitItem()[name]?.description).isEqualTo("English component")

            val saved = ComponentDetail(name = name, description = "")
            assertThat(details.saveComponentDetail(saved).first()).isTrue()

            assertThat(awaitItem()).containsExactly(name, saved)
            assertThat(details.getLocalComponentDetail(name).first()).isEqualTo(saved)
        }
    }

    private suspend fun install(sequence: Long = 1, components: String = LOCALIZED_COMPONENT, icon: String = "old icon") {
        val source = fixtureCatalog(sequence, rules = LOCALIZED_RULES, components = "$components\n", icon = icon)
        assertThat(installer.install(source)).isInstanceOf(CatalogInstallResult.Installed::class.java)
    }
}

private val LOCALIZED_RULES = """
    {"rules":[
      {"id":1,"icon":"$FIXTURE_ICON","website":"https://example.com","keywords":["second","first"],"safeToBlock":true,
       "contributors":["One","Two"],"i18n":{
         "en":{"name":"Example SDK","company":"Company","description":"English description","sideEffect":"English effect"},
         "zh-CN":{"name":"示例 SDK","description":"","sideEffect":"Localized effect"}}},
      {"id":2,"keywords":[],"contributors":[],"i18n":{"en":{"name":"Second SDK"},"zh-CN":{"description":"第二条"}}}
    ]}
""".trimIndent()

private val LOCALIZED_COMPONENT = """
    {"name":"$FIXTURE_COMPONENT_NAME","sdkRuleId":1,"recommendToBlock":true,"contributor":"Author",
     "updatedAt":"2026-10-20T00:00:00Z","i18n":{
       "en":{"description":"English component","disableEffect":"English effect"},"zh-CN":{"description":""}}}
""".trimIndent().lineSequence().joinToString("") { it.trim() }
