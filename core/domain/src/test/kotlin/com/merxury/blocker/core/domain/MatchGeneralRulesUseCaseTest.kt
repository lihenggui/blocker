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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MatchGeneralRulesUseCaseTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val components = TestComponentRepository()
    private val apps = TestAppRepository()
    private val users = TestUserDataRepository()
    private var snapshotReads = 0
    private val repository = object : ComponentRepository by components {
        override fun getAllComponents() = components.getAllComponents().also { snapshotReads++ }
        override fun searchComponent(keyword: String): Nothing = error("Matching must not query per keyword")
    }
    private val matcher = MatchGeneralRulesUseCase(repository, apps, users, dispatcherRule.testDispatcher)
    private val userComponent = ComponentInfo("com.user", "com.tracker.TrackerService", SERVICE)
    private val systemComponent = userComponent.copy(packageName = "com.system")
    private val rules = listOf(
        GeneralRule(1, "Tracker", searchKeyword = listOf("com.tracker", "TrackerService")),
        GeneralRule(2, "Shared keyword", searchKeyword = listOf("com.tracker")),
    )

    @Before
    fun setup() {
        users.sendUserData(defaultUserData)
        components.sendComponentList(listOf(userComponent, userComponent.copy(name = "com.tracker.OtherService"), systemComponent))
        apps.sendAppList(
            listOf(
                InstalledApp(packageName = "com.user", label = "User"),
                InstalledApp(packageName = "com.system", label = "System", isSystem = true),
            ),
        )
    }

    @Test
    fun givenHundredsOfRules_whenMatching_thenUsesOneSnapshotAndCountsDistinctApps() = runTest {
        val manyRules = List(512) { rules.first().copy(id = it) }
        val result = matcher(manyRules).first()

        assertEquals(1, snapshotReads)
        assertEquals(512, result.matchedAppCounts.size)
        assertEquals(setOf(1), result.matchedAppCounts.values.toSet())
        assertEquals(2, result.components.size)
    }

    @Test
    fun givenCachedMatches_whenControllerStatesChange_thenReusesMembershipWithFreshStates() = runTest {
        val first = matcher(rules).first()
        components.sendComponentList(
            components.getAllComponents().first().map { it.copy(pmBlocked = true, ifwBlocked = true) },
        )

        val second = matcher(rules.map { it.copy(name = "New label", matchedAppCount = 42) }).first()

        assertSame(first.matchedAppCounts, second.matchedAppCounts)
        assertEquals(first.components.map { it.copy(pmBlocked = true, ifwBlocked = true) }, second.components)
    }

    @Test
    fun givenCachedMatches_whenSystemVisibilityOrMetadataChanges_thenInvalidatesMatches() = runTest {
        val hidden = matcher(rules).first()
        users.sendUserData(defaultUserData.copy(showSystemApps = true))
        val shown = matcher(rules).first()
        assertEquals(mapOf(1 to 1, 2 to 1), hidden.matchedAppCounts)
        assertEquals(mapOf(1 to 2, 2 to 2), shown.matchedAppCounts)
        assertNotSame(hidden.matchedAppCounts, shown.matchedAppCounts)

        users.sendUserData(defaultUserData)
        apps.sendAppList(listOf(InstalledApp(packageName = "com.user", label = "User", isSystem = true)))
        val metadataChanged = matcher(rules).first()
        // Components without application metadata retain the old SQL path's inclusion behavior.
        assertEquals(listOf(systemComponent), metadataChanged.components)
    }

    @Test
    fun givenCachedMatches_whenAppsOrRulesChange_thenInvalidatesMatches() = runTest {
        val initial = matcher(rules).first()
        val installed = userComponent.copy(packageName = "com.installed")
        components.sendComponentList(initial.components + installed)
        assertEquals(mapOf(1 to 2, 2 to 2), matcher(rules).first().matchedAppCounts)

        components.sendComponentList(listOf(installed))
        assertEquals(listOf(installed), matcher(rules).first().components)
        assertEquals(emptyList(), matcher(listOf(rules.first().copy(searchKeyword = listOf("absent")))).first().components)
    }

    @Test
    fun givenStaleCounts_whenGatheringAndUpdatingCounts_thenSharesFreshMatches() = runTest {
        val generalRules = TestGeneralRuleRepository()
        generalRules.sendRuleList(rules)
        UpdateRuleMatchedAppsUseCase(generalRules, matcher)().first()
        val counted = matcher(rules).first()

        val gathered = GatherAllMatchedComponentsUseCase(generalRules, matcher)().first()

        assertEquals(counted.components, gathered)
        assertEquals(listOf(1, 1), generalRules.getGeneralRules().first().map { it.matchedAppCount })
        assertSame(counted.matchedAppCounts, matcher(rules).first().matchedAppCounts)
    }
}
