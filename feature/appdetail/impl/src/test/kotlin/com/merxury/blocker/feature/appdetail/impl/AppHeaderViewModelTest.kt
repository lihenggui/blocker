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

import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.domain.ZipAllRuleUseCase
import com.merxury.blocker.core.domain.ZipAppRuleUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.profile.ProfileLabel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AppHeaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fixture: AppDetailFixture

    private fun viewModel(userId: Int? = null): AppHeaderViewModel {
        fixture = AppDetailFixture(mainDispatcherRule.testDispatcher, userId = userId)
        val dispatcher = mainDispatcherRule.testDispatcher
        return AppHeaderViewModel(
            selectedUsers = fixture.selectedUsers,
            deviceUsers = fixture.deviceUsers,
            installedApps = fixture.installedApps,
            componentDetails = fixture.details,
            userDataRepository = fixture.userData,
            workManager = fixture.workManager,
            zipAppRule = ZipAppRuleUseCase(fixture.components, fixture.userData, tempFolder.newFolder(), tempFolder.newFolder(), "generated", dispatcher),
            zipAllRule = ZipAllRuleUseCase(fixture.userData, tempFolder.newFolder(), tempFolder.newFolder(), "generated", dispatcher),
            analyticsHelper = fixture.analytics,
            packageName = PACKAGE,
            userId = userId,
        )
    }

    private fun TestScope.collect(viewModel: AppHeaderViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private fun capturedInput(): Map<String, Any?> {
        val captor = argumentCaptor<OneTimeWorkRequest>()
        verify(fixture.workManager).enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), captor.capture())
        return captor.firstValue.workSpec.input.keyValueMap
    }

    private fun workEnds(name: String, vararg output: Pair<String, Any>) {
        whenever(fixture.workManager.getWorkInfosForUniqueWorkLiveData(name)) doReturn
            MutableLiveData(listOf(WorkInfo(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, emptySet(), workDataOf(*output))))
    }

    @Test
    fun givenNewViewModel_whenNothingCollects_thenNoWorkStarts() = runTest {
        viewModel()

        verify(fixture.workManager, never()).enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
    }

    @Test
    fun givenOwnUsersApp_whenShown_thenNoProfileAndLaunchedHere() = runTest {
        val viewModel = viewModel()
        fixture.installedApps.sendApps(listOf(installedApp(OWN_APP, label = "Example")))
        collect(viewModel)

        val state = viewModel.uiState.value
        assertThat(state.app?.label).isEqualTo("Example")
        assertThat(state.app?.icon?.uid).isEqualTo(10_100)
        assertThat(state.profile).isNull()
        assertThat(state.launch).isEqualTo(LaunchMode.OWN_USER)
    }

    @Test
    fun givenWorkProfileApp_whenShown_thenTheHeaderNamesTheProfileAndLaunchesThroughTheLauncher() = runTest {
        val viewModel = viewModel(userId = WORK.value)
        fixture.deviceUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp(WORK_APP)))
        collect(viewModel)

        val state = viewModel.uiState.value
        assertThat(state.app?.packageName).isEqualTo(PACKAGE)
        assertThat(state.profile).isEqualTo(ProfileLabel.Work)
        assertThat(state.launch).isEqualTo(LaunchMode.PROFILE)
        assertThat(state.app?.dataDir).isEqualTo("/data/user/10/$PACKAGE")
    }

    @Test
    fun givenSecondaryUsersApp_whenShown_thenItCannotBeLaunchedFromHere() = runTest {
        val guest = DeviceUser(UserId(11), name = "Guest", kind = UserKind.SECONDARY, parent = null, running = false)
        val viewModel = viewModel(userId = 11)
        fixture.deviceUsers.sendUsers(listOf(ownerUser, guest))
        fixture.installedApps.sendApps(listOf(installedApp(AppKey(PACKAGE, UserId(11)))))
        collect(viewModel)

        assertThat(viewModel.uiState.value.profile).isEqualTo(ProfileLabel.Named("Guest"))
        assertThat(viewModel.uiState.value.launch).isEqualTo(LaunchMode.NONE)
    }

    @Test
    fun givenLibCheckerInstalled_whenTheAppIsOnlyInTheWorkProfile_thenLibCheckerIsNotOffered() = runTest {
        val viewModel = viewModel(userId = WORK.value)
        fixture.installedApps.sendApps(listOf(installedApp(AppKey(LIBCHECKER_PACKAGE_NAME, OWNER)), installedApp(WORK_APP)))
        collect(viewModel)
        assertThat(viewModel.uiState.value.showLibChecker).isFalse()

        fixture.installedApps.sendApps(listOf(installedApp(AppKey(LIBCHECKER_PACKAGE_NAME, OWNER)), installedApp(WORK_APP), installedApp(OWN_APP)))
        assertThat(viewModel.uiState.value.showLibChecker).isTrue()
    }

    @Test
    fun givenWorkProfileApp_whenItsRulesAreExported_thenTheWorkTargetsThatUser() = runTest {
        val viewModel = viewModel(userId = WORK.value)
        workEnds("ExportBlockerRule:$PACKAGE:10", RuleWorkResult.PARAM_CHANGED_COUNT to 1)
        collect(viewModel)

        viewModel.exportBlockerRules()

        assertThat(capturedInput()).containsEntry("param_target_user", 10)
        assertThat(capturedInput()).containsEntry("param_backup_app_package_name", PACKAGE)
        assertThat(viewModel.uiState.value.ruleWork).isEqualTo(RuleWorkEvent(RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkResult.FINISHED, changed = 1))

        viewModel.onRuleWorkShown()
        assertThat(viewModel.uiState.value.ruleWork).isNull()
    }

    @Test
    fun givenWorkProfileApp_whenItsRulesAreImported_thenTheWorkRestoresThatUser() = runTest {
        val viewModel = viewModel(userId = WORK.value)
        workEnds("ImportBlockerRule:$PACKAGE:10")
        collect(viewModel)

        viewModel.importBlockerRules()

        assertThat(capturedInput()).containsEntry("param_target_user", 10)
    }

    @Test
    fun givenAnyApp_whenItsFirewallRulesAreReset_thenTheWorkNamesNoUser() = runTest {
        val viewModel = viewModel(userId = WORK.value)
        workEnds("ResetIfw:$PACKAGE:10")
        collect(viewModel)

        viewModel.resetIfw()

        verify(fixture.workManager).enqueueUniqueWork(eq("ResetIfw:$PACKAGE:10"), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        assertThat(capturedInput()).doesNotContainKey("param_target_user")
    }

    @Test
    fun givenNoDescriptionsWritten_whenTheFirstIsSaved_thenSharingIsOffered() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        assertThat(viewModel.uiState.value.hasCustomizedRules).isFalse()

        fixture.details.saveComponentDetail(ComponentDetail(name = id("Main").className, description = "The first screen")).collect()

        assertThat(viewModel.uiState.value.hasCustomizedRules).isTrue()
    }

    @Test
    fun givenNoRulesWritten_whenSharingTheAppsRules_thenAnEmptyZipIsHandedOver() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.shareAppRules()

        assertThat(viewModel.uiState.value.sharedRules?.zippedFile).isNull()
        viewModel.onShareHandled()
        assertThat(viewModel.uiState.value.sharedRules).isNull()
    }
}
