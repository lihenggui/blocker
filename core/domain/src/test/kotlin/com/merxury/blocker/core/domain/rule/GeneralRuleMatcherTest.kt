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

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.data.GeneralRule
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GeneralRuleMatcherTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val components = FakeComponentStatusRepository()
    private val apps = FakeInstalledAppRepository()
    private val rules = TestGeneralRuleRepository().apply { sendRuleList(listOf(TRACKER)) }
    private val userData = TestUserDataRepository().apply { sendUserData(defaultUserData) }

    private fun TestScope.matcher() = GeneralRuleMatcher(
        componentStatuses = components,
        installedApps = apps,
        generalRules = rules,
        userData = userData,
        selectedUsers = FakeSelectedUserRepository(OWNER),
        appPackageName = SELF_PACKAGE,
        cpuDispatcher = dispatcherRule.testDispatcher,
        scope = backgroundScope,
    )

    @Test
    fun givenBlockerInItsOwnUser_whenMatching_thenLeavesItOut() = runTest {
        apps.sendApps(listOf(installedApp(SELF_PACKAGE), installedApp("com.alpha")))
        components.sendComponents(OWNER, listOf(status(SELF_PACKAGE, "com.tracker.Self"), status("com.alpha", "com.tracker.Alpha")))

        val matches = matcher().matches(OWNER).first()

        assertThat(matches.appCountOf(TRACKER.id)).isEqualTo(1)
        assertThat(matches.componentsOf(TRACKER.id).map { it.declaration.id.packageName }).containsExactly("com.alpha")
    }

    @Test
    fun givenBlockerInAnotherUser_whenMatching_thenMatchesIt() = runTest {
        apps.sendApps(listOf(installedApp(SELF_PACKAGE, WORK)))
        components.sendComponents(WORK, listOf(status(SELF_PACKAGE, "com.tracker.Self")))

        assertThat(matcher().matches(WORK).first().appCountOf(TRACKER.id)).isEqualTo(1)
    }

    @Test
    fun givenTwoUsers_whenMatchingOne_thenOnlyItsAppsCount() = runTest {
        apps.sendApps(listOf(installedApp("com.alpha"), installedApp("com.alpha", WORK), installedApp("com.beta", WORK)))
        components.sendComponents(OWNER, listOf(status("com.alpha", "com.tracker.A")))
        components.sendComponents(WORK, listOf(status("com.alpha", "com.tracker.A"), status("com.beta", "com.tracker.B")))
        val matcher = matcher()

        assertThat(matcher.matches(OWNER).first().appCountOf(TRACKER.id)).isEqualTo(1)
        assertThat(matcher.matches(WORK).first().appCountOf(TRACKER.id)).isEqualTo(2)
    }

    @Test
    fun givenComponentsOfAnAppNotInstalled_whenMatching_thenLeavesThemOut() = runTest {
        apps.sendApps(listOf(installedApp("com.alpha")))
        components.sendComponents(OWNER, listOf(status("com.alpha", "com.tracker.A"), status("com.gone", "com.tracker.B")))

        assertThat(matcher().matches(OWNER).first().allComponents.map { it.declaration.id.packageName }).containsExactly("com.alpha")
    }

    @Test
    fun givenMatches_whenTheCacheStateChanges_thenEmitsTheNewState() = runTest {
        apps.sendApps(listOf(installedApp("com.alpha")))
        components.sendComponents(OWNER, listOf(status("com.alpha", "com.tracker.A")))

        matcher().matches(OWNER).test {
            assertThat(awaitItem().componentsOf(TRACKER.id).single().state.pm).isEqualTo(PmSetting.DEFAULT)
            components.sendComponents(OWNER, listOf(status("com.alpha", "com.tracker.A", pm = PmSetting.DISABLED)))
            assertThat(awaitItem().componentsOf(TRACKER.id).single().state.pm).isEqualTo(PmSetting.DISABLED)
        }
    }

    @Test
    fun givenMatches_whenTheKeywordsChange_thenMatchesAgain() = runTest {
        apps.sendApps(listOf(installedApp("com.alpha")))
        components.sendComponents(OWNER, listOf(status("com.alpha", "com.tracker.A"), status("com.alpha", "com.ads.B")))

        matcher().matches(OWNER).test {
            assertThat(awaitItem().componentsOf(TRACKER.id).map { it.declaration.id.className }).containsExactly("com.tracker.A")
            rules.sendRuleList(listOf(TRACKER.copy(searchKeyword = listOf("com.ads."))))
            assertThat(awaitItem().componentsOf(TRACKER.id).map { it.declaration.id.className }).containsExactly("com.ads.B")
        }
    }

    @Test
    fun givenSystemApps_whenTheUserShowsThem_thenTheyAreMatched() = runTest {
        apps.sendApps(listOf(installedApp("com.system", isSystem = true)))
        components.sendComponents(OWNER, listOf(status("com.system", "com.tracker.S")))

        matcher().matches(OWNER).test {
            assertThat(awaitItem().appCountOf(TRACKER.id)).isEqualTo(0)
            userData.sendUserData(defaultUserData.copy(showSystemApps = true))
            assertThat(awaitItem().appCountOf(TRACKER.id)).isEqualTo(1)
        }
    }

    @Test
    fun givenSeveralKeywords_whenMatching_thenEachAppListsItsComponentsAsTheOldRuleDetailDid() = runTest {
        rules.sendRuleList(listOf(TRACKER.copy(searchKeyword = listOf("com.second.", "com.first.", "Both"))))
        apps.sendApps(listOf(installedApp("com.alpha")))
        components.sendComponents(
            OWNER,
            listOf(
                status("com.alpha", "com.first.Activity", ComponentType.ACTIVITY),
                status("com.alpha", "com.first.Both", ComponentType.RECEIVER),
                status("com.alpha", "com.second.Provider", ComponentType.PROVIDER),
                status("com.alpha", "com.second.Receiver", ComponentType.RECEIVER),
                status("com.alpha", "com.second.Service", ComponentType.SERVICE),
                status("com.alpha", "com.second.ZActivity", ComponentType.ACTIVITY),
                status("com.alpha", "com.third.Both", ComponentType.ACTIVITY),
            ),
        )

        val names = matcher().matches(OWNER).first().componentsOf(TRACKER.id).map { it.declaration.id.className }

        // By the first keyword that matches, then activities, services, receivers and providers.
        assertThat(names).containsExactly(
            "com.second.ZActivity",
            "com.second.Service",
            "com.second.Receiver",
            "com.second.Provider",
            "com.first.Activity",
            "com.first.Both",
            "com.third.Both",
        ).inOrder()
    }

    private companion object {
        val TRACKER = GeneralRule(id = 1, name = "Tracker", searchKeyword = listOf("com.tracker."))
    }
}
