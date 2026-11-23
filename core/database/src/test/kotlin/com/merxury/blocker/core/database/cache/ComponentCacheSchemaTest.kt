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

package com.merxury.blocker.core.database.cache

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComponentCacheSchemaTest : ComponentCacheTestBase() {

    @Test
    fun givenVersionOne_whenReadingTheExportedSchema_thenItHoldsEveryCacheTable() {
        val schema = File("schemas/com.merxury.blocker.core.database.cache.ComponentCacheDatabase/1.json")

        val database = JSONObject(schema.readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val tables = List(entities.length()) { entities.getJSONObject(it).getString("tableName") }

        assertThat(database.getInt("version")).isEqualTo(1)
        assertThat(tables).containsExactly(
            "device_user",
            "app",
            "component_declaration",
            "component_scan",
            "component_pm_setting",
            "component_ifw_block",
        )
    }

    /**
     * The user's apps come from a covering index in package order, and each joined table is
     * searched by key. Only the class order within each package is sorted, never the whole result.
     */
    @Test
    fun whenPlanningAllComponentsOfAUser_thenEveryTableIsSearchedByIndex() {
        val sql = "EXPLAIN QUERY PLAN " + (ALL_COMPONENTS_QUERY + COMPONENT_ORDER).replace(":userId", "?")
        val plan = db.openHelper.readableDatabase.query(sql, arrayOf(OWNER))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) } }

        val searches = plan.filterNot { it.startsWith("USE TEMP B-TREE") }
        assertThat(searches.map { it.substringBefore(" USING") })
            .containsExactly("SEARCH a", "SEARCH d", "SEARCH p", "SEARCH i").inOrder()
        assertThat(searches.first()).contains("COVERING INDEX index_app_user_id_package_name")
        assertThat(plan).doesNotContain("USE TEMP B-TREE FOR ORDER BY")
    }

    /** Settings of one user, which both a user-wide read and the cascade from removing an app look up. */
    @Test
    fun whenPlanningSettingsOfAUser_thenTheUserIndexIsSearched() {
        val sql = "EXPLAIN QUERY PLAN SELECT * FROM component_pm_setting WHERE user_id = ? AND setting = ?"
        val plan = db.openHelper.readableDatabase.query(sql, arrayOf<Any>(OWNER, "DISABLED"))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) } }

        assertThat(plan.first()).startsWith("SEARCH component_pm_setting USING INDEX index_component_pm_setting_user_id_package_name")
    }

    @Test
    fun givenWildcardsAndEscapes_whenEscapingForLike_thenEachIsPrefixed() {
        assertThat("a_b%c\\d".escapeLikeWildcards()).isEqualTo("a\\_b\\%c\\\\d")
        assertThat("plain".escapeLikeWildcards()).isEqualTo("plain")
    }
}
