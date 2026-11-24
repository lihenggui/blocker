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

/**
 * The matching keeps what the matching over the old component table found for [MatchingData]:
 * these expectations are what the old matcher gave for it, compared with the new one on the same
 * data before the old one was removed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GeneralRuleMatchingTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userData = TestUserDataRepository()

    @Test
    fun givenSystemAppsHidden_whenMatching_thenFindsWhatTheOldMatchingFound() = runTest {
        userData.sendUserData(defaultUserData.copy(showSystemApps = false))
        val matches = matcher().matches(OWNER).first()

        assertThat(MatchingData.rules.associate { it.id to matches.appCountOf(it.id) })
            .containsExactly(1, 2, 2, 1, 3, 1, 4, 1, 5, 1, 6, 1, 7, 0, 8, 0, 9, 1)
        assertThat(names(matches, 1)).containsExactly("com.alpha/com.tracker.TrackerService", "com.alpha/com.tracker.Other", "com.gamma/com.gamma.MyTrackerService")
        assertThat(names(matches, 2)).containsExactly("com.alpha/com.tracker.TrackerService", "com.alpha/com.tracker.Other")
        assertThat(names(matches, 3)).containsExactly("com.alpha/com.ads.sdk.AdActivity")
        // `_` matches one character, so not the two of push__kit.
        assertThat(names(matches, 4)).containsExactly("com.beta/com.huawei.push.kit.Receiver", "com.beta/com.huawei.pushXkit.Receiver")
        assertThat(names(matches, 5)).containsExactly("com.beta/com.beta.analytics.core.BootReceiver", "com.beta/com.beta.Analytics.Receiver")
        assertThat(names(matches, 6)).containsExactly("com.gamma/com.gamma.übersicht.MainActivity")
        assertThat(names(matches, 7)).isEmpty()
        assertThat(names(matches, 8)).isEmpty()
        assertThat(names(matches, 9)).containsExactly("com.alpha/com.alpha.SyncAdapter")
        assertThat(matches.allComponents).hasSize(10)
    }

    @Test
    fun givenSystemAppsShown_whenMatching_thenTheirComponentsCountToo() = runTest {
        userData.sendUserData(defaultUserData.copy(showSystemApps = true))
        val matches = matcher().matches(OWNER).first()

        assertThat(MatchingData.rules.associate { it.id to matches.appCountOf(it.id) })
            .containsExactly(1, 3, 2, 2, 3, 2, 4, 1, 5, 1, 6, 1, 7, 0, 8, 0, 9, 1)
        assertThat(names(matches, 3)).containsExactly("com.alpha/com.ads.sdk.AdActivity", "com.system/com.ads.sdk.SystemAd")
        assertThat(matches.allComponents).hasSize(12)
    }

    private fun names(matches: GeneralRuleMatches, ruleId: Int) = matches.componentsOf(ruleId).map { it.declaration.id.flatten() }

    private fun TestScope.matcher() = GeneralRuleMatcher(
        componentStatuses = FakeComponentStatusRepository().apply { sendComponents(OWNER, MatchingData.components) },
        installedApps = FakeInstalledAppRepository().apply { sendApps(MatchingData.apps) },
        generalRules = TestGeneralRuleRepository().apply { sendRuleList(MatchingData.rules) },
        userData = userData,
        selectedUsers = FakeSelectedUserRepository(OWNER),
        appPackageName = SELF_PACKAGE,
        cpuDispatcher = dispatcherRule.testDispatcher,
        scope = backgroundScope,
    )
}
