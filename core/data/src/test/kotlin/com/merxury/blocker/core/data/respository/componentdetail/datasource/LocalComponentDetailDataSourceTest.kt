/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.data.respository.componentdetail.datasource

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import kotlin.time.Instant

class LocalComponentDetailDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = TestUserDataRepository()

    // The shared fake's getter is hardcoded to en-US; retain its preference flow
    // and setters while allowing these tests to exercise language changes.
    private val userDataRepository = object : UserDataRepository by preferences {
        override suspend fun getLibDisplayLanguage(): String = preferences.userData.first().libDisplayLanguage
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val component = ComponentDetail(
        name = "com.foo.Activity",
        sdkName = "Example SDK",
        description = "Description with a ] bracket and an escaped\nnewline",
        disableEffect = "Stops the example activity",
        contributor = "Contributor",
        recommendToBlock = true,
        lastUpdateTime = Instant.parse("2026-10-28T00:00:00Z"),
    )
    private lateinit var dataSource: LocalComponentDetailDataSource

    @Before
    fun setup() = runTest {
        preferences.setLibDisplayLanguage("en")
        dataSource = createDataSource(mainDispatcherRule.testDispatcher)
    }

    @Test
    fun given_bundle_when_get_by_component_name_then_returns_exact_detail() = runTest {
        writeBundle(listOf(component))
        writeLegacy(component.copy(description = "Legacy description"))

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(component)
        assertThat(dataSource.getByComponentName("com.foo.Activit").first()).isNull()
        assertThat(dataSource.getByComponentName("com.foo.ActivityExtra").first()).isNull()
    }

    @Test
    fun given_bundle_when_get_by_package_name_then_includes_subpackages_and_excludes_sibling_prefix() = runTest {
        val nested = component.copy(name = "com.foo.sub.Service")
        writeBundle(
            listOf(component, nested, component.copy(name = "com.foobar.Activity"), component.copy(name = "com.foo")),
        )

        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(component, nested).inOrder()
        assertThat(dataSource.getByPackageName("com.foo.sub").first()).containsExactly(nested)
        assertThat(dataSource.getByPackageName("com.missing").first()).isEmpty()
    }

    @Test
    fun given_missing_bundle_when_queries_run_then_falls_back_to_per_file_details() = runTest {
        val nested = component.copy(name = "com.foo.sub.Service")
        writeLegacy(component)
        writeLegacy(nested)
        writeLegacy(component.copy(name = "com.foobar.Activity"))

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(component)
        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(component, nested)
        assertThat(dataSource.getByComponentName("com.foo.Missing").first()).isNull()
    }

    @Test
    fun given_cached_bundle_when_file_content_and_timestamp_change_then_queries_reload() = runTest {
        val file = writeBundle(listOf(component))
        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(component)
        val originalTimestamp = file.lastModified()
        val updated = component.copy(name = "com.foo.UpdatedActivity", description = "Updated")
        writeBundle(listOf(updated))
        assertThat(file.setLastModified(originalTimestamp + 2_000)).isTrue()

        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(updated)
        assertThat(dataSource.getByComponentName(updated.name).first()).isEqualTo(updated)
        assertThat(dataSource.getByComponentName(component.name).first()).isNull()
    }

    @Test
    fun given_cached_bundle_when_only_length_changes_then_reloads() = runTest {
        val file = writeBundle(listOf(component))
        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(component)
        val originalTimestamp = file.lastModified()
        val originalLength = file.length()
        val updated = component.copy(description = "A different length")
        writeBundle(listOf(updated))
        assertThat(file.setLastModified(originalTimestamp)).isTrue()
        assertThat(file.length()).isNotEqualTo(originalLength)

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(updated)
    }

    @Test
    fun given_cached_bundle_when_only_timestamp_changes_then_reloads() = runTest {
        val file = writeBundle(listOf(component.copy(description = "First")))
        assertThat(dataSource.getByComponentName(component.name).first()?.description).isEqualTo("First")
        val originalTimestamp = file.lastModified()
        val originalLength = file.length()
        val updated = component.copy(description = "Other")
        writeBundle(listOf(updated))
        assertThat(file.setLastModified(originalTimestamp + 2_000)).isTrue()
        assertThat(file.length()).isEqualTo(originalLength)

        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(updated)
    }

    @Test
    fun given_language_bundles_with_same_metadata_when_language_changes_then_reads_other_bundle() = runTest {
        val english = component.copy(description = "English")
        val chinese = component.copy(description = "Chinese")
        val englishFile = writeBundle(listOf(english))
        val chineseFile = writeBundle(listOf(chinese), language = "zh-CN")
        assertThat(chineseFile.setLastModified(englishFile.lastModified())).isTrue()
        assertThat(chineseFile.length()).isEqualTo(englishFile.length())
        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(english)

        preferences.setLibDisplayLanguage("zh-CN")

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(chinese)
        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(chinese)
    }

    @Test
    fun given_malformed_lines_when_queries_run_then_skips_and_logs_invalid_entries() = runTest {
        val nested = component.copy(name = "com.foo.sub.Service")
        writeBundleText(
            "[\n${json.encodeToString(component)},\n" +
                "{broken json},\n" +
                "{\"name\": ]\n" +
                "{\"description\":\"Missing name\"},\n" +
                "${json.encodeToString(nested)}\n]\n",
        )
        val errors = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                errors.add(message)
            }
        }
        Timber.plant(tree)
        try {
            assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(component, nested)
            assertThat(dataSource.getByComponentName(nested.name).first()).isEqualTo(nested)
            assertThat(errors).hasSize(3)
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun given_unparseable_bundle_when_queries_run_then_falls_back_to_per_file_details() = runTest {
        writeBundleText("[\n{broken json}\n]\n")
        writeLegacy(component)

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(component)
        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(component)
    }

    @Test
    fun given_truncated_bundle_when_queries_run_then_discards_partial_index_and_uses_per_file_details() = runTest {
        writeBundleText("[\n${json.encodeToString(component)}\n")
        val legacy = component.copy(description = "Legacy description")
        writeLegacy(legacy)

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(legacy)
        assertThat(dataSource.getByPackageName("com.foo").first()).containsExactly(legacy)
    }

    @Test
    fun given_empty_bundle_when_queries_run_then_returns_empty_results() = runTest {
        writeBundleText("[]\n")
        writeLegacy(component)

        assertThat(dataSource.getByComponentName(component.name).first()).isNull()
        assertThat(dataSource.getByPackageName("com.foo").first()).isEmpty()
    }

    @Test
    fun given_missing_bundle_when_bundle_appears_then_uses_bundle() = runTest {
        writeLegacy(component)
        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(component)
        val updated = component.copy(description = "Bundle description")
        writeBundle(listOf(updated))

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(updated)
    }

    @Test
    fun given_cached_bundle_when_bundle_is_deleted_then_uses_per_file_details() = runTest {
        val file = writeBundle(listOf(component))
        val legacy = component.copy(description = "Legacy description")
        writeLegacy(legacy)
        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(component)
        assertThat(file.delete()).isTrue()

        assertThat(dataSource.getByComponentName(component.name).first()).isEqualTo(legacy)
    }

    @Test
    fun given_bundle_when_first_queries_run_concurrently_then_share_cached_detail_instances() = runTest {
        writeBundle(listOf(component))
        Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { dispatcher ->
            val concurrentSource = createDataSource(dispatcher)
            val details = List(20) {
                async { concurrentSource.getByComponentName(component.name).first() }
            }.awaitAll()

            assertThat(details.first()).isEqualTo(component)
            details.forEach { assertThat(it).isSameInstanceAs(details.first()) }
        }
    }

    private fun createDataSource(dispatcher: CoroutineDispatcher) = LocalComponentDetailDataSource(
        userDataRepository = userDataRepository,
        json = json,
        filesDir = temporaryFolder.root,
        ruleBaseFolder = "blocker-general-rules",
        ioDispatcher = dispatcher,
    )

    private fun writeBundle(components: List<ComponentDetail>, language: String = "en"): File = writeBundleText(
        components.sortedBy { it.name }.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]\n") {
            json.encodeToString(it)
        },
        language,
    )

    private fun writeBundleText(content: String, language: String = "en"): File = temporaryFolder.root
        .resolve("blocker-general-rules/component-details/$language.json")
        .apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    private fun writeLegacy(component: ComponentDetail): File = temporaryFolder.root
        .resolve("blocker-general-rules/components/en/${component.name.replace('.', '/')}.json")
        .apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString(component))
        }
}
