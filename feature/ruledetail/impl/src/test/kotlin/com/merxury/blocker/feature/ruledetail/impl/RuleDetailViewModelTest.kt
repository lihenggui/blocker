/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.ruledetail.impl

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType
import com.merxury.blocker.feature.ruledetail.api.navigation.RuleDetailTabs
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The rule detail lists the apps of the selected user with components the rule matches, from the cache. */
class RuleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fixture: RuleDetailFixture
    private val viewModel get() = fixture.viewModel

    @Before
    fun setUp() {
        fixture = RuleDetailFixture(mainDispatcherRule.testDispatcher)
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(
            listOf(
                installedApp("com.b", label = "Bravo", installedAt = 2_000),
                installedApp("com.a", label = "alpha", installedAt = 1_000),
                installedApp(SELF_PACKAGE, label = "Blocker"),
                installedApp("com.w", WORK, label = "Work app"),
            ),
        )
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "Pm", pm = PmSetting.DISABLED),
                component("com.a", "Ifw", ifw = IfwBlock.BLOCKED),
                component("com.a", "Shipped", enabledInManifest = false),
                component("com.a", "Unreadable", ifw = IfwBlock.UNKNOWN),
                component("com.b", "Open"),
                component(SELF_PACKAGE, "Self"),
            ),
        )
        fixture.components.sendComponents(WORK, listOf(component("com.w", "Work")))
    }

    private fun TestScope.show(keepFresh: Boolean = true) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        if (keepFresh) backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }
        settle()
    }

    @Test
    fun givenCachedComponents_whenShown_thenEachMatchedAppWithASwitchPerComponentByName() = runTest {
        show()

        val apps = fixture.apps()
        assertThat(apps.map { it.label }).containsExactly("alpha", "Bravo").inOrder()
        val alpha = apps.first()
        assertThat(alpha.app).isEqualTo(AppKey("com.a", OWNER))
        // Off when PackageManager, the firewall or the manifest blocks it; unreadable firewall rules count as none.
        assertThat(alpha.rows.associate { it.id.simpleName to it.enabled })
            .containsExactly("Ifw", false, "Pm", false, "Shipped", false, "Unreadable", true)
        // As before, which services run is not read, so no row shows one running.
        assertThat(apps.flatMap { it.rows }.none { it.isRunning }).isTrue()
    }

    @Test
    fun givenSeveralKeywords_whenAnAppIsShown_thenItsRowsKeepTheOldOrder() = runTest {
        fixture.rules.sendRuleList(listOf(TRACKER.copy(searchKeyword = listOf("com.tracker.Z", "com.tracker."))))
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "Activity", type = ComponentType.ACTIVITY),
                component("com.a", "Receiver"),
                component("com.a", "ZReceiver"),
                component("com.a", "ZService", type = ComponentType.SERVICE),
            ),
        )
        show()

        // The first keyword's components first, then by type, as the screen listed them before.
        assertThat(fixture.apps().single().rows.map { it.id.simpleName })
            .containsExactly("ZService", "ZReceiver", "Activity", "Receiver").inOrder()
    }

    @Test
    fun givenBlockerMatchesTheRule_whenShown_thenItIsNotListed() = runTest {
        show()

        assertThat(fixture.apps().map { it.app.packageName }).doesNotContain(SELF_PACKAGE)
    }

    @Test
    fun givenWorkProfileSelected_whenShown_thenItsAppsAreMatchedAndIndexed() = runTest {
        show()

        fixture.selectedUsers.select(WORK)
        settle()

        assertThat(fixture.apps().map { it.app }).containsExactly(AppKey("com.w", WORK))
        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER, WORK).inOrder()
    }

    @Test
    fun givenNothingObserves_whenCreated_thenNothingIsIndexed() = runTest {
        show(keepFresh = false)

        assertThat(fixture.components.refreshedUsers).isEmpty()
    }

    @Test
    fun givenTheRule_whenShown_thenItsIconIsAFileAndThemesTheScreenOnlyWithDynamicColour() = runTest {
        show()
        val icon = File("/files/catalog/icon-1/icon/tracker.svg")

        val info = viewModel.uiState.value.ruleInfo as RuleInfoUiState.Success
        assertThat(info.ruleInfo.iconUrl).isEqualTo(icon.absolutePath)
        assertThat(info.seedIcon).isNull()

        fixture.userData.sendUserData(defaultUserData.copy(useDynamicColor = true))
        settle()
        assertThat((viewModel.uiState.value.ruleInfo as RuleInfoUiState.Success).seedIcon).isEqualTo(icon)
    }

    @Test
    fun givenSortChosen_whenShown_thenTheAppsFollowIt() = runTest {
        show()

        viewModel.updateSortType(RuleDetailSortType.RECENTLY_INSTALLED)
        settle()
        assertThat(fixture.apps().map { it.label }).containsExactly("Bravo", "alpha").inOrder()
        viewModel.updateSortType(RuleDetailSortType.MOST_MATCHED)
        settle()
        assertThat(fixture.apps().map { it.label }).containsExactly("alpha", "Bravo").inOrder()
        assertThat(viewModel.uiState.value.sortType).isEqualTo(RuleDetailSortType.MOST_MATCHED)
    }

    @Test
    fun givenSortByMostBlocked_whenAComponentIsBlocked_thenItsAppKeepsItsPlace() = runTest {
        show()
        viewModel.updateSortType(RuleDetailSortType.MOST_BLOCKED)
        settle()
        assertThat(fixture.apps().map { it.label }).containsExactly("alpha", "Bravo").inOrder()

        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "Pm"),
                component("com.b", "Open", pm = PmSetting.DISABLED),
                component("com.b", "Other", pm = PmSetting.DISABLED),
            ),
        )
        settle()

        assertThat(fixture.apps().map { it.label }).containsExactly("alpha", "Bravo").inOrder()
        assertThat(fixture.apps().last().rows.map { it.enabled }).containsExactly(false, false)
    }

    @Test
    fun givenDescriptionTab_whenSwitched_thenTheAppBarHasNoActions() = runTest {
        show()
        assertThat(viewModel.uiState.value.appBarUiState.actions).containsExactly(AppBarAction.SORT, AppBarAction.MORE)

        viewModel.switchTab(RuleDetailTabs.Description)
        settle()

        assertThat(viewModel.uiState.value.tabState.selectedItem).isEqualTo(RuleDetailTabs.Description)
        assertThat(viewModel.uiState.value.appBarUiState.actions).isEmpty()
    }

    @Test
    fun givenMatchedApps_whenTheirActivityOrServiceIsUsed_thenTheSelectedUserIsAsked() = runTest {
        show()
        fixture.selectedUsers.select(WORK)
        settle()

        viewModel.launchActivity(ComponentKey(id("com.w", "Work"), WORK))
        settle()

        assertThat(fixture.appControl.calls.single()).contains("UserId(value=10)")
        assertThat(viewModel.uiState.value.actionFailure).isNull()
    }

    @Test
    fun givenNoAccess_whenAnActivityCannotBeLaunched_thenTellsItWithTheAccessHint() = runTest {
        fixture.appControl.failures["startActivity"] = CallFailure.ChannelUnavailable
        show()

        viewModel.launchActivity(ComponentKey(id("com.a", "Pm"), OWNER))
        settle()

        assertThat(viewModel.uiState.value.actionFailure).isEqualTo(ActivityLaunchFailed("Pm", needsAccess = true))
        viewModel.onActionFailureShown()
        settle()
        assertThat(viewModel.uiState.value.actionFailure).isNull()
    }

    @Test
    fun givenNoRuleMatchedYet_whenOpened_thenOnlyThisRuleIsMatched() = runTest {
        show()

        assertThat(fixture.apps().map { it.label }).containsExactly("alpha", "Bravo")
        assertThat(fixture.rules.catalogReads).isEqualTo(0)
    }

    @Test
    fun givenTheRuleCannotBeRead_whenOpened_thenTheErrorShowsUntilItCanBeReadAgain() = runTest {
        fixture.rules.readFailure = IllegalStateException("The rule file is damaged")
        show()
        assertThat(viewModel.uiState.value.ruleInfo).isInstanceOf(RuleInfoUiState.Error::class.java)

        fixture.rules.readFailure = null
        // Longer than the matcher and the screen wait before they read the rule again.
        advanceTimeBy(20_000)
        settle()

        assertThat(fixture.apps().map { it.label }).containsExactly("alpha", "Bravo")
    }
}
