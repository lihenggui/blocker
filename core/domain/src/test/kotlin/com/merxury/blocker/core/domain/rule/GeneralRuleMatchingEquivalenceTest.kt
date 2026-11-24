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

package com.merxury.blocker.core.domain.rule

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.domain.MatchGeneralRulesUseCase
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.model.data.InstalledApp as LegacyInstalledApp

/**
 * The matching over the component cache finds what the matching over the old component table
 * found for the same apps and components: the same apps per rule and the same components, for
 * every rule alone and for all of them together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GeneralRuleMatchingEquivalenceTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userData = TestUserDataRepository()

    @Test
    fun givenSystemAppsHidden_whenMatchingTheCache_thenFindsWhatTheOldTableMatchingFound() = runTest {
        userData.sendUserData(defaultUserData.copy(showSystemApps = false))
        assertSameMatches()
    }

    @Test
    fun givenSystemAppsShown_whenMatchingTheCache_thenFindsWhatTheOldTableMatchingFound() = runTest {
        userData.sendUserData(defaultUserData.copy(showSystemApps = true))
        assertSameMatches()
    }

    private suspend fun TestScope.assertSameMatches() {
        val old = oldMatcher()
        val new = newMatcher().matches(OWNER).first()
        val oldAll = old(MatchingData.rules).first()

        MatchingData.rules.forEach { rule ->
            assertThat(new.appCountOf(rule.id)).isEqualTo(oldAll.matchedAppCounts.getValue(rule.id))
            val oldAlone = old(listOf(rule)).first().components.map { it.packageName to it.name }
            assertThat(new.componentsOf(rule.id).map { it.declaration.id.packageName to it.declaration.id.className })
                .containsExactlyElementsIn(oldAlone)
        }
        assertThat(new.allComponents.map { it.declaration.id.packageName to it.declaration.id.className })
            .containsExactlyElementsIn(oldAll.components.map { it.packageName to it.name })
        // The data set is matched by something, so the comparison is not empty on both sides.
        assertThat(oldAll.matchedAppCounts.values.sum()).isGreaterThan(5)
    }

    private fun oldMatcher(): MatchGeneralRulesUseCase {
        val components = TestComponentRepository().apply {
            sendComponentList(
                MatchingData.components.map {
                    ComponentInfo(
                        packageName = it.declaration.id.packageName,
                        name = it.declaration.id.className,
                        type = it.declaration.type,
                        pmBlocked = it.state.pm == PmSetting.DISABLED,
                        ifwBlocked = it.state.ifw == IfwBlock.BLOCKED,
                    )
                },
            )
        }
        val apps = TestAppRepository().apply {
            sendAppList(MatchingData.apps.map { LegacyInstalledApp(packageName = it.key.packageName, label = it.label, isSystem = it.isSystem) })
        }
        return MatchGeneralRulesUseCase(components, apps, userData, dispatcherRule.testDispatcher)
    }

    private fun TestScope.newMatcher(): GeneralRuleMatcher {
        val components = FakeComponentStatusRepository().apply { sendComponents(OWNER, MatchingData.components) }
        val apps = FakeInstalledAppRepository().apply { sendApps(MatchingData.apps) }
        val rules = TestGeneralRuleRepository().apply { sendRuleList(MatchingData.rules) }
        return GeneralRuleMatcher(
            componentStatuses = components,
            installedApps = apps,
            generalRules = rules,
            userData = userData,
            selectedUsers = FakeSelectedUserRepository(OWNER),
            appPackageName = SELF_PACKAGE,
            cpuDispatcher = dispatcherRule.testDispatcher,
            scope = backgroundScope,
        )
    }
}
