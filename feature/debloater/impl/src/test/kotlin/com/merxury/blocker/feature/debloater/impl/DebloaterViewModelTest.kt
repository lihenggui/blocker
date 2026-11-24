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

package com.merxury.blocker.feature.debloater.impl

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.DebloaterMetadata
import com.merxury.blocker.core.data.device.RefreshProgress
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.control.ControlMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebloaterViewModelTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun givenCachedApps_whenScreenStarts_thenIndexAndShowAppsInPackageManagerOrder() = runTest {
        val f = fixture()
        f.install(installed("example.z"), installed())
        f.viewModel.uiState.test {
            assertThat(awaitItem().content).isEqualTo(Result.Loading)
            settle()
            assertThat(expectMostRecentItem().apps().map { it.app.packageName }).containsExactly("example.z", APP).inOrder()
            assertThat(f.components.refreshedUsers).containsExactly(owner)
        }
    }

    @Test
    fun givenIndexingUser_whenProgressArrives_thenKeepExistingLoadingUntilFinished() = runTest {
        val f = fixture()
        val finish = CompletableDeferred<Unit>()
        f.install(installed())
        f.components.refreshAllProgress = {
            flow {
                emit(RefreshProgress.Refreshing(1, 2, APP))
                finish.await()
                emit(RefreshProgress.Finished(2, 0, null))
            }
        }
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().content).isEqualTo(Result.Loading)
            finish.complete(Unit)
            settle()
            assertThat(expectMostRecentItem().apps().single().app.userId).isEqualTo(owner)
        }
    }

    @Test
    fun givenEachUserHasTheSamePackage_whenSelectionChanges_thenHideOldRowsAndUseTheNewUser() = runTest {
        val f = fixture()
        val finish = CompletableDeferred<Unit>()
        f.install(installed(label = "Owner app"), installed(user = work, label = "Work app"))
        f.components.refreshAllProgress = { user ->
            flow {
                if (user == work) finish.await()
                emit(RefreshProgress.Finished(1, 0, null))
            }
        }
        f.viewModel.uiState.test {
            settle()
            val old = expectMostRecentItem().apps().single()
            assertThat(old.title).isEqualTo("Owner app")
            f.users.select(work)
            f.viewModel.setEnabled(old.targets.first(), enabled = false)
            f.viewModel.setAllEnabled(old.app, old.targets, enabled = false)
            settle()
            assertThat(expectMostRecentItem().content).isEqualTo(Result.Loading)
            assertThat(f.started).isEmpty()
            finish.complete(Unit)
            settle()
            val app = expectMostRecentItem().apps().single()
            assertThat(app.title).isEqualTo("Work app")
            assertThat(app.targets.map { it.key.userId }.distinct()).containsExactly(work)
            f.viewModel.setEnabled(app.targets.first(), enabled = false)
            settle()
            assertThat(f.started.single().user).isEqualTo(work)
            assertThat(expectMostRecentItem().apps().single().targets.first().component.enabled).isFalse()
        }
    }

    @Test
    fun givenWorkProfileSelected_whenRepositoryDropsIt_thenAutomaticallyReturnToOwner() = runTest {
        val f = fixture()
        f.install(installed(label = "Owner app"), installed(user = work, label = "Work app"))
        f.users.select(work)
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps().single().title).isEqualTo("Work app")
            f.users.sendUsers(listOf(ownerUser))
            settle()
            assertThat(expectMostRecentItem().apps().single().title).isEqualTo("Owner app")
            assertThat(f.components.refreshedUsers).containsExactly(work, owner).inOrder()
        }
    }

    @Test
    fun givenSelfInstalledForBothUsers_whenOpeningEachUser_thenHideOnlyTheRunningInstance() = runTest {
        val f = fixture()
        f.install(installed(SELF), installed(SELF, work), installed())
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps().map { it.app.packageName }).containsExactly(APP)
            assertThat(f.metadata.reads.map { it.packageName }).doesNotContain(SELF)
            f.users.select(work)
            settle()
            assertThat(expectMostRecentItem().apps().single().app.packageName).isEqualTo(SELF)
        }
    }

    @Test
    fun givenClassifiedRows_whenFilteringAndSearching_thenKeepLegacySearchAndAnyKindMatch() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().names()).containsExactly("Share", "Link", "PushService").inOrder()
            f.viewModel.updateSearchQuery("EXAMPLE")
            settle()
            assertThat(expectMostRecentItem().names()).containsExactly("Share", "Link", "PushService").inOrder()
            f.viewModel.updateComponentTypeFilter(setOf(ComponentClassification.SHAREABLE, ComponentClassification.PUSH_SERVICE))
            settle()
            assertThat(expectMostRecentItem().names()).containsExactly("Share", "PushService").inOrder()
            f.viewModel.updateSearchQuery("other apps")
            settle()
            assertThat(expectMostRecentItem().names()).containsExactly("Share")
            f.viewModel.updateSearchQuery("Link")
            settle()
            assertThat(expectMostRecentItem().names()).isEmpty()
            f.viewModel.updateComponentTypeFilter(emptySet())
            settle()
            assertThat(expectMostRecentItem().names()).containsExactly("Link")
        }
    }

    @Test
    fun givenEveryClassification_whenFiltering_thenKeepTheLegacyMembership() = runTest {
        val f = fixture()
        val app = installed("example")
        f.install(app)
        f.metadata.manifests[app.key] = DebloaterMetadata(classificationManifest, classificationPermissions)
        f.components.sendComponents(
            owner,
            listOf(
                status(app, "Share"),
                status(app, "LaunchFromLink"),
                status(app, "ViewOnly"),
                status(app, "MyPushService", ComponentType.SERVICE),
                status(app, "Boot", ComponentType.RECEIVER),
                status(app, "Provider", ComponentType.PROVIDER),
            ),
        )
        val expected = mapOf(
            ComponentClassification.SHAREABLE to listOf("Share"),
            ComponentClassification.DEEPLINK to emptyList(),
            ComponentClassification.LAUNCHER to emptyList(),
            ComponentClassification.WAKELOCK to listOf("Share", "ViewOnly", "MyPushService", "Boot", "Provider"),
            ComponentClassification.AUTO_START to listOf("Boot"),
            ComponentClassification.EXPORTED_NO_PERM to listOf("Share", "Provider"),
            ComponentClassification.FOREGROUND_SERVICE to listOf("MyPushService"),
            ComponentClassification.PUSH_SERVICE to listOf("MyPushService"),
            ComponentClassification.DANGEROUS_PROVIDER to listOf("Provider"),
        )
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().names()).containsExactlyElementsIn(expected.getValue(ComponentClassification.WAKELOCK)).inOrder()
            expected.forEach { (kind, names) ->
                f.viewModel.updateComponentTypeFilter(setOf(kind))
                settle()
                assertThat(expectMostRecentItem().names()).containsExactlyElementsIn(names).inOrder()
            }
        }
    }

    @Test
    fun givenHiddenSystemApps_whenShowSystemAppsChanges_thenIncludeThem() = runTest {
        val f = fixture()
        f.install(installed(system = true))
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).isEmpty()
            f.preferences.setShowSystemApps(true)
            settle()
            assertThat(expectMostRecentItem().apps().single().app.packageName).isEqualTo(APP)
        }
    }

    @Test
    fun givenDifferentReadBackStates_whenMappingRows_thenUseSharedSwitchRules() = runTest {
        val f = fixture()
        val app = installed()
        f.install(app)
        f.components.sendComponents(
            owner,
            listOf(
                status(app, "Share", ifw = IfwBlock.CONDITIONAL),
                status(app, "Link", pm = PmSetting.DISABLED, ifw = IfwBlock.UNKNOWN),
                status(app, "PushService", ComponentType.SERVICE, shippedEnabled = false, ifw = IfwBlock.UNKNOWN),
            ),
        )
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps().single().targets.map { it.component.enabled }).containsExactly(true, false, false).inOrder()
        }
    }

    @Test
    fun givenBlockedAndEnabledRows_whenToggling_thenUseSettingsForBlockAndAllLayersForEnable() = runTest {
        val f = fixture()
        f.install(installed())
        f.preferences.setControllerType(ControllerType.IFW)
        f.viewModel.uiState.test {
            settle()
            val row = expectMostRecentItem().apps().single().targets.first()
            f.controlGate = CompletableDeferred()
            f.viewModel.setEnabled(row, enabled = false)
            settle()
            val pending = expectMostRecentItem()
            assertThat(pending.inFlight).containsExactly(row.key)
            assertThat(pending.apps().single().targets.first().component.enabled).isTrue()
            assertThat(f.started.single().goal).isEqualTo(ControlGoal.BLOCK)
            assertThat(f.started.single().layers).containsExactly(ControlLayer.INTENT_FIREWALL)
            f.controlGate.complete(Unit)
            settle()
            val blocked = expectMostRecentItem()
            assertThat(blocked.inFlight).isEmpty()
            assertThat(blocked.messages).isEmpty()
            assertThat(blocked.apps().single().targets.first().component.enabled).isFalse()
            f.viewModel.setEnabled(blocked.apps().single().targets.first(), enabled = true)
            settle()
            val enabled = expectMostRecentItem()
            assertThat(f.started.last().goal).isEqualTo(ControlGoal.ENABLE)
            assertThat(f.started.last().layers).containsExactlyElementsIn(ControlLayer.entries)
            assertThat(enabled.apps().single().targets.first().component.enabled).isTrue()
            assertThat(enabled.messages).isEmpty()
            assertThat(f.metadata.reads).containsExactly(installed().key)
        }
    }

    @Test
    fun givenFailedSwitches_whenOutcomesArrive_thenOfferAccessOnlyForMissingAccess() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            settle()
            val item = expectMostRecentItem().apps().single().targets.first()
            f.components.controlEvents = { request ->
                val failure = if (request.goal == ControlGoal.BLOCK) ControlFailure.ChannelUnavailable else ControlFailure.StateNotConfirmed
                val outcome = ComponentOutcome(
                    item.key,
                    status(installed(), "Share").state,
                    ControlResult.FAILED,
                    mapOf(ControlLayer.PACKAGE_MANAGER to failure),
                )
                listOf(ControlEvent.PackageDone(APP, listOf(outcome)), ControlEvent.Finished(0, 0, 1))
            }
            f.viewModel.setEnabled(item, enabled = false)
            settle()
            val message = expectMostRecentItem().messages.single()
            assertThat(message.message).isEqualTo(ControlMessage.ComponentNotChanged("Share", ControlGoal.BLOCK, needsAccess = true))
            f.viewModel.grantAccess()
            f.viewModel.onMessageShown(message.id)
            f.viewModel.setEnabled(item, enabled = true)
            settle()
            assertThat(f.channel.connectCount).isEqualTo(1)
            assertThat(expectMostRecentItem().messages.single().message).isEqualTo(ControlMessage.ComponentNotChanged("Share", ControlGoal.ENABLE))
        }
    }

    @Test
    fun givenFilteredApp_whenBlockingAndEnablingAll_thenApplyToShownRowsAndCompleteSilently() = runTest {
        val f = fixture()
        f.install(installed(user = work))
        f.users.select(work)
        f.viewModel.updateComponentTypeFilter(setOf(ComponentClassification.SHAREABLE))
        f.viewModel.uiState.test {
            settle()
            val app = expectMostRecentItem().apps().single()
            f.controlGate = CompletableDeferred()
            f.viewModel.setAllEnabled(app.app, app.targets, enabled = false)
            settle()
            val pending = expectMostRecentItem()
            val row = app.targets.single()
            assertThat(pending.inFlight).containsExactly(row.key)
            assertThat(f.started.single().components.map { it.id }).containsExactly(row.key.id)
            assertThat(f.started.single().user).isEqualTo(work)
            assertThat(f.started.single().layers).containsExactly(ControlLayer.PACKAGE_MANAGER)
            f.controlGate.complete(Unit)
            settle()
            val blocked = expectMostRecentItem()
            assertThat(blocked.inFlight).isEmpty()
            assertThat(blocked.messages).isEmpty()
            assertThat(blocked.apps().single().targets.single().component.enabled).isFalse()
            f.viewModel.setAllEnabled(app.app, app.targets, enabled = true)
            settle()
            assertThat(f.started.last().goal).isEqualTo(ControlGoal.ENABLE)
            assertThat(f.started.last().layers).containsExactlyElementsIn(ControlLayer.entries)
            assertThat(expectMostRecentItem().apps().single().targets.single().component.enabled).isTrue()
        }
    }

    @Test
    fun givenBatchPreparing_whenViewModelClears_thenBatchStillRunsForOriginalUser() = runTest {
        val f = fixture()
        f.install(installed())
        lateinit var key: ComponentKey
        f.viewModel.uiState.test {
            settle()
            val app = expectMostRecentItem().apps().single()
            key = app.targets.first().key
            f.preparationGate = CompletableDeferred()
            f.viewModel.setAllEnabled(app.app, app.targets, enabled = false)
            settle()
            assertThat(f.started).isEmpty()
            assertThat(f.batches.isInFlight(key)).isTrue()
            assertThat(expectMostRecentItem().inFlight).contains(key)
        }
        f.viewModel.viewModelScope.cancel()
        f.users.select(work)
        f.preparationGate.complete(Unit)
        settle()
        assertThat(f.started.single().user).isEqualTo(owner)
        assertThat(f.batches.current(BatchKey.Debloater(owner))).isNull()
        assertThat(f.batches.isInFlight(key)).isFalse()
    }

    @Test
    fun givenIndexingFailure_whenRepositoryThrows_thenShowExistingErrorState() = runTest {
        val f = fixture()
        val failure = IllegalStateException("Index failed")
        f.components.refreshAllProgress = { flow { throw failure } }
        f.viewModel.uiState.test {
            settle()
            assertThat((expectMostRecentItem().content as Result.Error).exception).hasMessageThat().isEqualTo(failure.message)
        }
    }

    @Test
    fun givenMetadataFailure_whenReadingMatches_thenShowExistingErrorState() = runTest {
        val f = fixture()
        val failure = IllegalStateException("Read failed")
        f.install(installed())
        f.metadata.failure = failure
        f.viewModel.uiState.test {
            settle()
            assertThat((expectMostRecentItem().content as Result.Error).exception).hasMessageThat().isEqualTo(failure.message)
        }
    }

    @Test
    fun givenPermissionChanges_whenObserved_thenKeepExistingPermissionWarningState() = runTest {
        val f = fixture()
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().hasRootPermission).isTrue()
            f.permission.value = PermissionStatus.NO_PERMISSION
            settle()
            assertThat(expectMostRecentItem().hasRootPermission).isFalse()
        }
    }

    private fun TestScope.fixture() = DebloaterFixture(backgroundScope, main.testDispatcher)

    private fun TestScope.settle() {
        advanceTimeBy(500)
        runCurrent()
    }

    private fun DebloaterUiState.apps() = (content as Result.Success).data

    private fun DebloaterUiState.names() = apps().flatMap { app -> app.targets.map { it.key.id.simpleName } }
}
