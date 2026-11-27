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

package com.merxury.blocker.feature.appdetail.impl

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.appcontrol.ServiceControl
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.component.ComponentRow
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailTabs
import com.merxury.blocker.feature.appdetail.api.navigation.FIRST_COMPONENT_TAB
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ComponentListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var fixture = AppDetailFixture(mainDispatcherRule.testDispatcher)
    private val viewModel get() = fixture.list

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private fun TestScope.keepFresh(): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }

    private fun TestScope.show() {
        collectState()
        keepFresh()
    }

    private fun row(name: String) = fixture.loaded().run { receivers + services + activities + providers }.single { it.id == id(name) }

    @Test
    fun givenNewViewModel_whenNothingCollects_thenNoWorkStarts() = runTest {
        fixture.sendComponents(component("Main"))

        assertThat(viewModel.uiState.value.components).isEqualTo(ComponentsUiState.Loading)
        assertThat(fixture.components.refreshedApps).isEmpty()
        assertThat(fixture.serviceControl.runningRequests).isEmpty()
        assertThat(fixture.channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenEveryState_whenShown_thenEachSwitchIsOffExactlyWhenSomethingKnownBlocks() = runTest {
        fixture.sendComponents(
            component("Runnable"),
            component("ByPm", pm = PmSetting.DISABLED),
            component("ByIfw", ifw = IfwBlock.BLOCKED),
            component("ByManifest", enabledInManifest = false),
            component("EnabledOverManifest", pm = PmSetting.ENABLED, enabledInManifest = false),
            component("Conditional", ifw = IfwBlock.CONDITIONAL),
            component("IfwUnknown", ifw = IfwBlock.UNKNOWN),
            component("PmUnknown", pm = PmSetting.UNKNOWN),
        )

        show()

        assertThat(row("Runnable")).isEqualTo(ComponentRow(id("Runnable"), ComponentType.ACTIVITY, enabled = true))
        val enabled = listOf("ByPm", "ByIfw", "ByManifest", "EnabledOverManifest", "Conditional", "IfwUnknown", "PmUnknown").associateWith { row(it).enabled }
        assertThat(enabled).containsExactly(
            "ByPm", false,
            "ByIfw", false,
            "ByManifest", false,
            "EnabledOverManifest", true,
            "Conditional", true,
            "IfwUnknown", true,
            "PmUnknown", true,
        )
    }

    @Test
    fun givenFirewallNotReadable_whenShown_thenThePackageManagerStateDecides() = runTest {
        fixture.components.setFirewallReadable(false)
        fixture.sendComponents(component("Main", ifw = IfwBlock.UNKNOWN), component("Off", pm = PmSetting.DISABLED, ifw = IfwBlock.UNKNOWN))

        show()

        assertThat(row("Main").enabled).isTrue()
        assertThat(row("Off").enabled).isFalse()
    }

    @Test
    fun givenProviderWithUnreadableRules_whenShown_thenOnlyItsSettingCounts() = runTest {
        fixture.sendComponents(component("Files", type = ComponentType.PROVIDER, ifw = IfwBlock.UNKNOWN))

        show()

        assertThat(fixture.loaded().providers.single().enabled).isTrue()
    }

    @Test
    fun givenScreenShown_whenShownAgainAfterRotation_thenRefreshedOnce() = runTest {
        fixture.sendComponents(component("Main"))
        collectState()

        keepFresh().cancel()
        keepFresh()

        assertThat(fixture.components.refreshedApps).containsExactly(OWN_APP)
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun givenCachedRows_whenRefreshFails_thenRowsStayAndTheReasonIsTold() = runTest {
        fixture.components.refreshFailure = ReadFailure.RulesUnreadable(PACKAGE)
        fixture.sendComponents(component("Main", pm = PmSetting.DISABLED))

        show()

        val state = viewModel.uiState.value
        assertThat(row("Main").enabled).isFalse()
        assertThat(state.message).isEqualTo(ListMessage.RefreshFailed(ReadFailure.RulesUnreadable(PACKAGE)))
        assertThat(state.isRefreshing).isFalse()

        viewModel.onMessageShown()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun givenNothingCached_whenRefreshFails_thenEmptyListsInsteadOfLoading() = runTest {
        fixture.components.refreshFailure = ReadFailure.PackageNotFound(OWN_APP)

        show()

        assertThat(fixture.loaded()).isEqualTo(AppComponents())
        assertThat(viewModel.uiState.value.tabs.items).containsExactly(AppDetailTabs.Info)
        assertThat(viewModel.uiState.value.refreshFailure).isEqualTo(ReadFailure.PackageNotFound(OWN_APP))
    }

    @Test
    fun givenFailedRefresh_whenRefreshedAgainSuccessfully_thenNoFailureIsKept() = runTest {
        fixture.components.refreshFailure = ReadFailure.UserNotReachable(OWNER)
        show()
        assertThat(viewModel.uiState.value.refreshFailure).isEqualTo(ReadFailure.UserNotReachable(OWNER))

        fixture.components.refreshFailure = null
        viewModel.refresh()

        assertThat(viewModel.uiState.value.refreshFailure).isNull()
    }

    @Test
    fun givenRulesNotReadYet_whenTheCacheEmits_thenRowsAreShownAndOnlyTheMatchedRulesLoad() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, rulesRead = false)
        fixture.sendComponents(component("firebase.Messaging", type = ComponentType.SERVICE), component("Main"))

        show()

        assertThat(fixture.loaded().services.map { it.id }).containsExactly(id("firebase.Messaging"))
        assertThat(viewModel.uiState.value.matchedRules).isEqualTo(MatchedRulesUiState.Loading)
        assertThat(viewModel.uiState.value.tabs.items).contains(AppDetailTabs.Sdk)

        fixture.rules.sendRuleList(listOf(GeneralRule(id = 7, name = "Firebase", searchKeyword = listOf("firebase"))))

        val rule = (viewModel.uiState.value.matchedRules as MatchedRulesUiState.Loaded).rules.single()
        assertThat(rule.rows.map { it.id }).containsExactly(id("firebase.Messaging"))
    }

    @Test
    fun givenDescriptionsNotReadYet_whenTheCacheEmits_thenRowsAreShownWithoutThem() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, descriptionsRead = false)
        fixture.sendComponents(component("Main"))

        show()

        assertThat(row("Main").description).isNull()

        fixture.sendDescriptions("Main" to "The first screen")

        assertThat(row("Main").description).isEqualTo("The first screen")
    }

    @Test
    fun givenShownRows_whenSearchingOrRefreshing_thenNeitherSortedNorMatchedAgain() = runTest {
        fixture.rules.sendRuleList(listOf(GeneralRule(id = 7, name = "Firebase", searchKeyword = listOf("firebase"))))
        fixture.sendComponents(component("firebase.Messaging", type = ComponentType.SERVICE), component("Main"))
        show()
        val sorted = fixture.loaded()
        val matched = viewModel.uiState.value.matchedRules

        viewModel.refresh()
        assertThat(fixture.loaded()).isSameInstanceAs(sorted)
        assertThat(viewModel.uiState.value.matchedRules).isSameInstanceAs(matched)

        viewModel.setSearchMode(true)
        viewModel.search("main")
        advanceTimeBy(300)
        runCurrent()
        assertThat(viewModel.uiState.value.matchedRules).isSameInstanceAs(matched)

        fixture.userData.sendUserData(defaultUserData.copy(useDynamicColor = !defaultUserData.useDynamicColor))
        viewModel.search("")
        assertThat(fixture.loaded()).isSameInstanceAs(sorted)
    }

    @Test
    fun givenAccessUnchecked_whenShownOrPulled_thenNothingIsReadAndNoFailureIsTold() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, userId = WORK.value)
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        fixture.channel.firstCheckPending = true
        fixture.components.refreshFailure = ReadFailure.UserNotReachable(WORK)
        fixture.sendComponents(component("Main"))
        show()

        viewModel.refresh()

        assertThat(fixture.components.refreshedApps).isEmpty()
        assertThat(viewModel.uiState.value.message).isNull()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun givenAccessUnchecked_whenTheCheckSettlesReady_thenRefreshedOnceWithoutAFailure() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, userId = WORK.value)
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        fixture.channel.firstCheckPending = true
        show()

        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))

        assertThat(fixture.components.refreshedApps).containsExactly(WORK_APP)
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun givenAccessUnchecked_whenTheCheckSettlesWithoutAccess_thenRefreshedOnce() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        fixture.channel.firstCheckPending = true
        show()

        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))

        assertThat(fixture.components.refreshedApps).containsExactly(OWN_APP)
    }

    @Test
    fun givenAccessCheckPending_whenPulled_thenRefreshedOnceTheCheckSettles() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        fixture.channel.firstCheckPending = true
        fixture.sendComponents(component("Main"))
        show()

        viewModel.refresh()
        assertThat(fixture.components.refreshedApps).isEmpty()

        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))

        assertThat(fixture.components.refreshedApps).containsExactly(OWN_APP)
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun givenAccessCheckThatNeverSettles_whenShown_thenToldInsteadOfLoadingForever() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        fixture.channel.firstCheckPending = true
        show()
        assertThat(viewModel.uiState.value.components).isEqualTo(ComponentsUiState.Loading)

        advanceTimeBy(10_001)

        val state = viewModel.uiState.value
        assertThat(state.message).isEqualTo(ListMessage.AccessCheckFailed)
        assertThat(state.accessCheckFailed).isTrue()
        assertThat(state.components).isEqualTo(ComponentsUiState.Loaded(AppComponents()))
        assertThat(fixture.components.refreshedApps).isEmpty()
    }

    @Test
    fun givenAccessCheckThatFailed_whenAccessIsGrantedLater_thenRefreshed() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        fixture.channel.firstCheckPending = true
        show()
        advanceTimeBy(10_001)

        viewModel.grantAccess()

        assertThat(fixture.components.refreshedApps).containsExactly(OWN_APP)
        assertThat(viewModel.uiState.value.accessCheckFailed).isFalse()
    }

    @Test
    fun givenSearchOnAComponentTab_whenTheMatchedRulesTabIsSelected_thenSearchModeEnds() = runTest {
        fixture.rules.sendRuleList(listOf(GeneralRule(id = 7, name = "Firebase", searchKeyword = listOf("firebase"))))
        fixture.sendComponents(component("firebase.Receiver", type = ComponentType.RECEIVER), component("Main"))
        show()
        viewModel.setSearchMode(true)
        viewModel.search("main")
        advanceTimeBy(300)
        runCurrent()

        viewModel.selectTab(AppDetailTabs.Sdk)

        assertThat(viewModel.searchQuery.value).isNull()
        assertThat(fixture.loaded().receivers).hasSize(1)
    }

    @Test
    fun givenAccessReady_whenLostAndRegained_thenRefreshedOnlyWhenReadyAgain() = runTest {
        show()
        assertThat(fixture.components.refreshedApps).hasSize(1)

        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.FAILED))
        assertThat(fixture.components.refreshedApps).hasSize(1)

        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        assertThat(fixture.components.refreshedApps).hasSize(2)

        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        assertThat(fixture.components.refreshedApps).hasSize(3)
    }

    @Test
    fun givenRunningService_whenShown_thenMarkedAndListedFirst() = runTest {
        fixture.serviceControl.running[OWN_APP] = mutableSetOf(id("Zed"))
        fixture.sendComponents(component("Alpha", type = ComponentType.SERVICE), component("Zed", type = ComponentType.SERVICE))

        show()

        assertThat(fixture.loaded().services.map { it.id.simpleName to it.isRunning }).containsExactly("Zed" to true, "Alpha" to false).inOrder()
    }

    @Test
    fun givenRunningServicesNotPermitted_whenShown_thenNoMarkers() = runTest {
        fixture.serviceControl.running[OWN_APP] = mutableSetOf(id("Sync"))
        fixture.serviceControl.runningFailure = CallFailure.NotPermitted("Root sees running processes of user 0 only")
        fixture.sendComponents(component("Sync", type = ComponentType.SERVICE))

        show()

        assertThat(row("Sync").isRunning).isFalse()
    }

    @Test
    fun givenNoChannel_whenShown_thenRunningServicesAreNotAskedFor() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        fixture.sendComponents(component("Sync", type = ComponentType.SERVICE))

        show()

        assertThat(fixture.serviceControl.runningRequests).isEmpty()
    }

    @Test
    fun givenStateChanges_whenShown_thenRunningServicesReadAgain() = runTest {
        fixture.sendComponents(component("Sync", type = ComponentType.SERVICE))
        show()
        val before = fixture.serviceControl.runningRequests.size

        fixture.sendComponents(component("Sync", type = ComponentType.SERVICE, pm = PmSetting.DISABLED))
        advanceTimeBy(500)

        assertThat(fixture.serviceControl.runningRequests.size).isEqualTo(before + 1)
    }

    @Test
    fun givenRunningService_whenStopped_thenStoppedAndRunningReadAgain() = runTest {
        fixture.serviceControl.running[OWN_APP] = mutableSetOf(id("Sync"))
        fixture.sendComponents(component("Sync", type = ComponentType.SERVICE))
        show()

        viewModel.stopService(id("Sync"))

        assertThat(row("Sync").isRunning).isFalse()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun givenStopRefused_whenStopped_thenTheReasonIsTold() = runTest {
        fixture.services = object : ServiceControl by fixture.serviceControl {
            override suspend fun stop(service: ComponentKey): CallResult<Unit> = CallResult.Failure(CallFailure.NotPermitted("not exported"))
        }
        fixture.sendComponents(component("Sync", type = ComponentType.SERVICE))
        show()

        viewModel.stopService(id("Sync"))

        assertThat(viewModel.uiState.value.message).isEqualTo(ListMessage.StopFailed("Sync", ActionFailure.NotPermitted))
    }

    @Test
    fun givenRunnableActivity_whenLaunched_thenStartedWithoutAMessage() = runTest {
        fixture.sendComponents(component("Main"))
        show()

        viewModel.launchActivity(id("Main"))

        assertThat(fixture.appControl.calls).contains("startActivity ${ComponentKey(id("Main"), OWNER)}")
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun givenActivityBlockedByIfw_whenLaunchAccepted_thenNothingIsTold() = runTest {
        fixture.sendComponents(component("Ad", ifw = IfwBlock.BLOCKED), component("Share", ifw = IfwBlock.CONDITIONAL))
        show()

        viewModel.launchActivity(id("Ad"))
        viewModel.launchActivity(id("Share"))

        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun givenLaunchRefused_whenLaunched_thenTheReasonIsTold() = runTest {
        fixture.appControl.failures["startActivity"] = CallFailure.SystemError("code -92")
        fixture.sendComponents(component("Main"))
        show()

        viewModel.launchActivity(id("Main"))

        assertThat(viewModel.uiState.value.message).isEqualTo(ListMessage.LaunchFailed("Main", ActionFailure.SystemError("code -92")))
    }

    @Test
    fun givenAnotherUsersApp_whenShown_thenEverythingIsForThatUser() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, userId = WORK.value)
        fixture.components.sendComponents(OWNER, listOf(component("OwnerOnly")))
        fixture.sendComponents(component("Main"), component("Sync", type = ComponentType.SERVICE))
        show()

        viewModel.launchActivity(id("Main"))

        assertThat(fixture.loaded().activities.map { it.id }).containsExactly(id("Main"))
        assertThat(fixture.components.refreshedApps).containsExactly(WORK_APP)
        assertThat(fixture.serviceControl.runningRequests).contains(WORK_APP)
        assertThat(fixture.appControl.calls).contains("startActivity ${ComponentKey(id("Main"), WORK)}")
    }

    @Test
    fun givenKeywords_whenSearching_thenOnlyMatchingRowsAndCountsShown() = runTest {
        fixture.sendComponents(component("SyncService", type = ComponentType.SERVICE), component("PushService", type = ComponentType.SERVICE), component("Main"))
        show()

        viewModel.setSearchMode(true)
        viewModel.search("sync, main")
        advanceTimeBy(300)
        runCurrent()

        assertThat(fixture.loaded().services.map { it.id.simpleName }).containsExactly("SyncService")
        assertThat(fixture.loaded().activities.map { it.id.simpleName }).containsExactly("Main")
        assertThat(viewModel.uiState.value.tabs.itemCount[AppDetailTabs.Service]).isEqualTo(1)

        viewModel.setSearchMode(false)
        assertThat(fixture.loaded().services).hasSize(2)
        assertThat(viewModel.searchQuery.value).isNull()
    }

    @Test
    fun givenLoadedRows_whenTyping_thenUpdateTheFieldImmediatelyAndFilterAfterThePause() = runTest {
        fixture = AppDetailFixture(StandardTestDispatcher(testScheduler))
        fixture.sendComponents(component("Main"), component("Other"))
        collectState()
        runCurrent()

        viewModel.setSearchMode(true)
        viewModel.search("Ma")
        assertThat(viewModel.searchQuery.value).isEqualTo("Ma")
        advanceTimeBy(200)
        viewModel.search("Main")
        assertThat(viewModel.searchQuery.value).isEqualTo("Main")
        advanceTimeBy(299)
        runCurrent()
        assertThat(fixture.loaded().activities.map { it.id }).containsExactly(id("Main"), id("Other"))

        advanceTimeBy(1)
        runCurrent()
        assertThat(fixture.loaded().activities.map { it.id }).containsExactly(id("Main"))

        viewModel.search("")
        assertThat(viewModel.searchQuery.value).isEmpty()
        runCurrent()
        assertThat(fixture.loaded().activities.map { it.id }).containsExactly(id("Main"), id("Other"))
    }

    @Test
    fun givenSavedSearchQuery_whenRecreated_thenRestoreTheFieldAndFilterTheFirstRows() = runTest {
        val saved = SavedStateHandle()
        AppDetailFixture(mainDispatcherRule.testDispatcher, savedStateHandle = saved).list.search("Main")
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, savedStateHandle = restored, searchKeyword = listOf("Other"))
        fixture.sendComponents(component("Main"), component("Other"))

        assertThat(viewModel.searchQuery.value).isEqualTo("Main")
        collectState()
        assertThat(fixture.loaded().activities.map { it.id }).containsExactly(id("Main"))
    }

    @Test
    fun givenKeywordsFromNavigation_whenShown_thenSearchModeIsOn() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, searchKeyword = listOf(" push ", ""))
        fixture.sendComponents(component("SyncService", type = ComponentType.SERVICE), component("PushService", type = ComponentType.SERVICE))

        show()

        assertThat(viewModel.searchQuery.value).isEqualTo("push")
        assertThat(fixture.loaded().services.map { it.id.simpleName }).containsExactly("PushService")
    }

    @Test
    fun givenSortPreferences_whenShown_thenRowsFollowThem() = runTest {
        fixture.sendComponents(component("a.Zed"), component("b.Alpha", pm = PmSetting.DISABLED), component("c.Mid"))
        show()

        fixture.userData.sendUserData(defaultUserData.copy(componentSorting = ComponentSorting.COMPONENT_NAME, componentSortingOrder = SortingOrder.DESCENDING))
        assertThat(fixture.loaded().activities.map { it.id.simpleName }).containsExactly("Zed", "Mid", "Alpha").inOrder()

        fixture.userData.sendUserData(defaultUserData.copy(componentSorting = ComponentSorting.PACKAGE_NAME))
        assertThat(fixture.loaded().activities.map { it.id.simpleName }).containsExactly("Zed", "Alpha", "Mid").inOrder()

        fixture.userData.sendUserData(defaultUserData.copy(componentShowPriority = ComponentShowPriority.DISABLED_COMPONENTS_FIRST))
        assertThat(fixture.loaded().activities.map { it.id.simpleName }).containsExactly("Alpha", "Mid", "Zed").inOrder()
    }

    @Test
    fun givenRequestedTab_whenItHasNoRows_thenTheAppInfoIsSelected() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, tab = "provider")
        collectState()

        // While loading every component tab is shown, so the requested one stays selected.
        assertThat(viewModel.uiState.value.tabs.selectedItem).isEqualTo(AppDetailTabs.Provider)

        fixture.sendComponents(component("Main"))

        val tabs = viewModel.uiState.value.tabs
        assertThat(tabs.items).containsExactly(AppDetailTabs.Info, AppDetailTabs.Activity).inOrder()
        assertThat(tabs.selectedItem).isEqualTo(AppDetailTabs.Info)
    }

    @Test
    fun givenFirstComponentTabRequested_whenComponentsLoad_thenOpensFirstAvailableComponentTab() = runTest {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, tab = FIRST_COMPONENT_TAB)
        collectState()
        fixture.sendComponents(component("Main"), component("Data", type = ComponentType.PROVIDER))

        assertThat(viewModel.uiState.value.tabs.selectedItem).isEqualTo(AppDetailTabs.Activity)
    }

    @Test
    fun givenMatchingGeneralRule_whenShown_thenItsComponentsAreGrouped() = runTest {
        fixture.rules.sendRuleList(listOf(GeneralRule(id = 7, name = "Firebase", iconUrl = "/files/catalog/icon-1/icon/firebase.png", searchKeyword = listOf("firebase"))))
        fixture.sendComponents(component("firebase.Messaging", type = ComponentType.SERVICE, ifw = IfwBlock.BLOCKED), component("Main"))

        show()

        val rule = (viewModel.uiState.value.matchedRules as MatchedRulesUiState.Loaded).rules.single()
        assertThat(rule.title).isEqualTo("Firebase")
        assertThat(rule.icon).isEqualTo(File("/files/catalog/icon-1/icon/firebase.png"))
        assertThat(rule.rows.map { it.id to it.enabled }).containsExactly(id("firebase.Messaging") to false)
        assertThat(viewModel.uiState.value.tabs.items).contains(AppDetailTabs.Sdk)
    }

    @Test
    fun givenDescriptions_whenShown_thenRowsCarryThem() = runTest {
        fixture.sendDescriptions("Main" to "The first screen")
        fixture.sendComponents(component("Main"))

        show()

        assertThat(row("Main").description).isEqualTo("The first screen")
    }

    @Test
    fun givenNoAccess_whenGrantIsRefused_thenToldAccessIsStillMissing() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        fixture.channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)
        show()

        viewModel.grantAccess()

        assertThat(fixture.channel.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.message).isEqualTo(ListMessage.GrantFailed)
    }

    @Test
    fun givenNoAccess_whenGranted_thenRefreshedWithAccess() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        show()

        viewModel.grantAccess()

        assertThat(fixture.channel.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
        assertThat(fixture.components.refreshedApps).hasSize(2)
    }

    @Test
    fun givenSelectionMode_whenAllSelectedTwice_thenTheTabIsSelectedThenCleared() = runTest {
        fixture.sendComponents(component("A", type = ComponentType.RECEIVER), component("B", type = ComponentType.RECEIVER), component("Main"))
        show()

        viewModel.setSelectionMode(true)
        viewModel.toggleSelected(id("A"))
        viewModel.toggleSelectAllInTab()
        assertThat(viewModel.uiState.value.selected).containsExactly(id("A"), id("B"))

        viewModel.toggleSelectAllInTab()
        assertThat(viewModel.uiState.value.selected).isEmpty()

        viewModel.toggleSelected(id("B"))
        viewModel.setSelectionMode(false)
        assertThat(viewModel.uiState.value.let { it.isSelectionMode to it.selected }).isEqualTo(false to emptySet<Any>())
    }

    @Test
    fun givenSelectionMode_whenTheMatchedRulesTabIsSelected_thenSelectionModeEnds() = runTest {
        fixture.rules.sendRuleList(listOf(GeneralRule(id = 7, name = "Firebase", searchKeyword = listOf("firebase"))))
        fixture.sendComponents(component("firebase.Receiver", type = ComponentType.RECEIVER))
        show()
        viewModel.setSelectionMode(true)
        viewModel.toggleSelected(id("firebase.Receiver"))

        viewModel.selectTab(AppDetailTabs.Sdk)

        assertThat(viewModel.uiState.value.let { it.isSelectionMode to it.selected }).isEqualTo(false to emptySet<Any>())
    }

    @Test
    fun givenMatchedRulesTab_whenItsPageIsTaken_thenEveryMatchedComponentOnce() = runTest {
        fixture.rules.sendRuleList(
            listOf(
                GeneralRule(id = 1, name = "Firebase", searchKeyword = listOf("firebase")),
                GeneralRule(id = 2, name = "Messaging", searchKeyword = listOf("messaging")),
            ),
        )
        fixture.sendComponents(
            component("firebase.Messaging", type = ComponentType.SERVICE),
            component("firebase.Init", type = ComponentType.PROVIDER),
            component("other.MessagingReceiver", type = ComponentType.RECEIVER),
            component("Main"),
        )
        show()

        viewModel.selectTab(AppDetailTabs.Sdk)

        assertThat(viewModel.uiState.value.componentsOfSelectedPage())
            .containsExactly(id("firebase.Init"), id("firebase.Messaging"), id("other.MessagingReceiver"))
        viewModel.selectTab(AppDetailTabs.Activity)
        assertThat(viewModel.uiState.value.componentsOfSelectedPage()).containsExactly(id("Main"))
        viewModel.selectTab(AppDetailTabs.Info)
        assertThat(viewModel.uiState.value.componentsOfSelectedPage()).isEmpty()
    }
}
