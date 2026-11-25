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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
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
import kotlin.time.Instant

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
            assertThat(f.viewModel.uiState.value.names()).containsExactly("Share", "Link", "PushService").inOrder()
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
    fun givenLoadedRows_whenTyping_thenUpdateTheFieldImmediatelyAndFilterAfterThePause() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            runCurrent()
            assertThat(expectMostRecentItem().names()).containsExactly("Share", "Link", "PushService")
            f.viewModel.updateSearchQuery("Sha")
            assertThat(f.viewModel.searchQuery.value).isEqualTo("Sha")
            advanceTimeBy(200)
            f.viewModel.updateSearchQuery("Share")
            assertThat(f.viewModel.searchQuery.value).isEqualTo("Share")
            advanceTimeBy(299)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertThat(expectMostRecentItem().names()).containsExactly("Share")

            f.viewModel.updateSearchQuery("")
            assertThat(f.viewModel.searchQuery.value).isEmpty()
            runCurrent()
            assertThat(expectMostRecentItem().names()).containsExactly("Share", "Link", "PushService")
        }
    }

    @Test
    fun givenSavedSearchQuery_whenRecreated_thenRestoreTheFieldAndFilterTheFirstRows() = runTest {
        val saved = SavedStateHandle()
        fixture(saved).viewModel.updateSearchQuery("push")
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val f = fixture(restored)
        f.install(installed())

        assertThat(f.viewModel.searchQuery.value).isEqualTo("push")
        f.viewModel.uiState.test {
            runCurrent()
            assertThat(expectMostRecentItem().names()).containsExactly("PushService")
        }
    }

    @Test
    fun givenEveryClassification_whenFiltering_thenKeepTheLegacyMembership() = runTest {
        val f = fixture()
        val app = installed("example")
        f.install(app)
        f.metadata.manifests[app.key] = classificationManifest
        f.metadata.apks[app.key] = f.metadata.apks.getValue(app.key).copy(requestedPermissions = classificationPermissions)
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
    fun givenSystemApps_whenTogglingTheirVisibility_thenReuseTheirClassifications() = runTest {
        val f = fixture()
        f.install(installed(system = true))
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).isEmpty()
            f.preferences.setShowSystemApps(true)
            settle()
            assertThat(expectMostRecentItem().apps().single().app.packageName).isEqualTo(APP)
            f.preferences.setShowSystemApps(false)
            settle()
            assertThat(expectMostRecentItem().apps()).isEmpty()
            f.preferences.setShowSystemApps(true)
            settle()
            assertThat(expectMostRecentItem().apps().single().app.packageName).isEqualTo(APP)
            assertThat(f.metadata.reads).containsExactly(installed().key)
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
    fun givenComponentVanished_whenSwitchedOff_thenTellsItCouldNotBeBlocked() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            settle()
            val item = expectMostRecentItem().apps().single().targets.first()
            f.components.sendComponents(owner, emptyList())

            f.viewModel.setEnabled(item, enabled = false)
            settle()

            assertThat(f.started).isEmpty()
            assertThat(expectMostRecentItem().messages.single().message).isEqualTo(ControlMessage.ComponentNotChanged("Share", ControlGoal.BLOCK))
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
        assertThat(f.batches.current(BatchKey.Debloater(installed().key))).isNull()
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
    fun givenPendingPermissionCheck_whenItAnswersAndThenGrants_thenStateReflectsEachResult() = runTest {
        val f = fixture()
        f.permission.value = null
        f.install(installed())
        f.viewModel.uiState.test {
            assertThat(awaitItem().hasRootPermission).isNull()
            settle()
            val pending = expectMostRecentItem()
            assertThat(pending.hasRootPermission).isNull()
            assertThat(pending.apps()).hasSize(1)

            f.permission.value = PermissionStatus.NO_PERMISSION
            settle()
            assertThat(expectMostRecentItem().hasRootPermission).isFalse()

            f.permission.value = PermissionStatus.ROOT_USER
            settle()
            assertThat(expectMostRecentItem().hasRootPermission).isTrue()
        }
    }

    @Test
    fun givenLoadedRows_whenResubscribingAfterTimeout_thenKeepRowsWithoutLoadingOrParsingAgain() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).hasSize(1)
        }
        advanceTimeBy(5_001)
        runCurrent()

        f.viewModel.uiState.test {
            assertThat(awaitItem().apps()).hasSize(1)
            settle()
            expectNoEvents()
            assertThat(f.metadata.reads).containsExactly(installed().key)
        }
    }

    @Test
    fun givenStoppedSubscription_whenTheUserChanges_thenReplayOnlyRowsForTheSelectedUser() = runTest {
        val f = fixture()
        f.install(installed(label = "Owner app"), installed(user = work, label = "Work app"))
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps().single().title).isEqualTo("Owner app")
        }
        advanceTimeBy(5_001)
        runCurrent()
        f.users.select(work)

        assertThat(f.viewModel.uiState.value.content).isEqualTo(Result.Loading)
        f.viewModel.uiState.test {
            assertThat(awaitItem().content).isEqualTo(Result.Loading)
            settle()
            assertThat(expectMostRecentItem().apps().single().title).isEqualTo("Work app")
        }
    }

    @Test
    fun givenLoadedRows_whenTheChannelChanges_thenKeepRowsWithoutLoadingOrParsingAgain() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).hasSize(1)

            f.channel.setState(ChannelState.Unavailable(UnavailableReason.FAILED))
            settle()

            expectNoEvents()
            assertThat(f.metadata.reads).containsExactly(installed().key)
        }
    }

    @Test
    fun givenLoadedRows_whenTheIndexRunsAgain_thenKeepRowsWithoutLoadingOrParsingAgain() = runTest {
        val f = fixture()
        f.install(installed())
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).hasSize(1)
            val finish = CompletableDeferred<Unit>()
            f.components.refreshAllProgress = {
                flow {
                    emit(RefreshProgress.Refreshing(0, 1, APP))
                    finish.await()
                    emit(RefreshProgress.Finished(1, 0, null))
                }
            }

            f.index.reindex(owner)
            settle()
            expectNoEvents()
            finish.complete(Unit)
            settle()

            expectNoEvents()
            assertThat(f.metadata.reads).containsExactly(installed().key)
        }
    }

    @Test
    fun givenStaleCachedPath_whenTheLiveApkChanges_thenReadTheNewRevisionFromItsLivePath() = runTest {
        val f = fixture()
        val app = installed().copy(apkPath = "/removed/base.apk")
        f.install(app)
        f.viewModel.uiState.test {
            settle()
            val first = f.metadata.readApks.single()
            assertThat(first.path).isNotEqualTo(app.apkPath)
            assertThat(expectMostRecentItem().apps()).hasSize(1)

            val replacement = first.copy(path = "/updated/base.apk", lastUpdateTime = 1, versionCode = 2)
            f.metadata.apks[app.key] = replacement
            f.index.reindex(owner)
            settle()

            assertThat(f.metadata.readApks).containsExactly(first, replacement).inOrder()
            assertThat(f.viewModel.uiState.value.apps()).hasSize(1)
        }
    }

    @Test
    fun givenLoadedRows_whenCachedUpdateTimeChanges_thenTheLiveManifestMemoIsInvalidated() = runTest {
        val f = fixture()
        val app = installed()
        f.install(app)
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).hasSize(1)
            val apk = f.metadata.readApks.single()
            val updated = apk.copy(lastUpdateTime = app.lastUpdateTime.toEpochMilliseconds() + 1)
            f.metadata.apks[app.key] = updated
            f.apps.sendApps(listOf(app.copy(lastUpdateTime = Instant.fromEpochMilliseconds(updated.lastUpdateTime))))
            settle()

            assertThat(f.metadata.readApks).containsExactly(apk, updated).inOrder()
            assertThat(f.viewModel.uiState.value.apps()).hasSize(1)
        }
    }

    @Test
    fun givenUnreadableApk_whenReindexed_thenRetryOnlyAfterTheLiveRevisionChanges() = runTest {
        val f = fixture()
        val app = installed()
        f.install(app)
        val manifest = f.metadata.manifests.remove(app.key)!!
        val apk = f.metadata.apks.getValue(app.key)
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps()).isEmpty()

            f.index.reindex(owner)
            settle()
            assertThat(f.metadata.readApks).containsExactly(apk)

            val updated = apk.copy(lastUpdateTime = 1)
            f.metadata.apks[app.key] = updated
            f.metadata.manifests[app.key] = manifest
            f.index.reindex(owner)
            settle()

            assertThat(f.metadata.readApks).containsExactly(apk, updated).inOrder()
            assertThat(expectMostRecentItem().apps()).hasSize(1)
        }
    }

    @Test
    fun givenWorkRows_whenPackageReadsBecomeUnavailable_thenKeepThemInCachedAppOrder() = runTest {
        val f = fixture()
        val first = installed("example.first", work)
        val second = installed("example.second", work)
        f.install(first, second)
        f.metadata.order[work] = listOf(second.key.packageName, first.key.packageName)
        f.users.select(work)
        f.viewModel.uiState.test {
            settle()
            assertThat(expectMostRecentItem().apps().map { it.app }).containsExactly(second.key, first.key).inOrder()

            val unavailable = IllegalStateException()
            f.metadata.failure = unavailable
            f.metadata.orderFailure = unavailable
            f.channel.setState(ChannelState.Unavailable(UnavailableReason.FAILED))
            settle()

            assertThat(expectMostRecentItem().apps().map { it.app }).containsExactly(first.key, second.key).inOrder()
            assertThat(f.metadata.reads).containsExactly(second.key, first.key).inOrder()
        }
    }

    @Test
    fun givenTwoApps_whenBatchesOverlapAndTheFirstIsTappedAgain_thenRunOneBatchPerApp() = runTest {
        val f = fixture()
        f.install(installed(), installed("example.second"))
        f.viewModel.uiState.test {
            settle()
            val (first, second) = expectMostRecentItem().apps()
            f.controlGate = CompletableDeferred()

            f.viewModel.setAllEnabled(first.app, first.targets, enabled = false)
            f.viewModel.setAllEnabled(second.app, second.targets, enabled = false)
            f.viewModel.setAllEnabled(first.app, first.targets, enabled = true)
            settle()

            assertThat(f.started.map { it.components.first().id.packageName }).containsExactly(first.app.packageName, second.app.packageName)
            assertThat(f.started.map { it.goal }).containsExactly(ControlGoal.BLOCK, ControlGoal.BLOCK)
            assertThat(expectMostRecentItem().inFlight).containsExactlyElementsIn((first.targets + second.targets).map { it.key })

            f.controlGate.complete(Unit)
            settle()
            assertThat(expectMostRecentItem().inFlight).isEmpty()
            assertThat(f.viewModel.uiState.value.messages).isEmpty()
        }
    }

    @Test
    fun givenBatchWithSomeFailedComponents_whenItEnds_thenQueueTheSharedSummaryAndOfferAccess() = runTest {
        val f = fixture()
        val installedApp = installed()
        f.install(installedApp)
        f.viewModel.uiState.test {
            settle()
            val app = expectMostRecentItem().apps().single()
            f.components.controlEvents = { request ->
                val outcomes = request.components.mapIndexed { index, declaration ->
                    val failed = index == 0
                    ComponentOutcome(
                        ComponentKey(declaration.id, request.user),
                        status(installedApp, "Share", pm = if (failed) PmSetting.DEFAULT else PmSetting.DISABLED).state,
                        if (failed) ControlResult.FAILED else ControlResult.ACHIEVED,
                        if (failed) mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable) else emptyMap(),
                    )
                }
                listOf(ControlEvent.PackageDone(APP, outcomes), ControlEvent.Finished(outcomes.size - 1, 0, 1))
            }

            f.viewModel.setAllEnabled(app.app, app.targets, enabled = false)
            settle()

            val state = expectMostRecentItem()
            assertThat(state.messages.single().message).isEqualTo(ControlMessage.BatchDone(notChanged = 1, needsAccess = true))
            assertThat(state.apps().single().targets.first().component.enabled).isTrue()
            f.viewModel.grantAccess()
            f.viewModel.onMessageShown(state.messages.single().id)
            settle()
            assertThat(f.channel.connectCount).isEqualTo(1)
            assertThat(expectMostRecentItem().messages).isEmpty()
        }
    }

    @Test
    fun givenBatchPreparationThrows_whenTheBatchEnds_thenShowAnAlert() = runTest {
        val f = fixture()
        f.install(installed())
        f.preparationFailure = IllegalStateException("Cannot prepare changes")
        f.viewModel.uiState.test {
            settle()
            val app = expectMostRecentItem().apps().single()
            f.viewModel.setAllEnabled(app.app, app.targets, enabled = false)
            settle()

            val state = expectMostRecentItem()
            assertThat(state.alert?.title).isEqualTo("Cannot prepare changes")
            assertThat(state.inFlight).isEmpty()
            assertThat(f.started).isEmpty()
        }
    }

    @Test
    fun givenBatchErrorAlert_whenDismissed_thenClearTheAlert() = runTest {
        val f = fixture()
        f.install(installed())
        f.preparationFailure = IllegalStateException("Cannot prepare changes")
        f.viewModel.uiState.test {
            settle()
            val app = expectMostRecentItem().apps().single()
            f.viewModel.setAllEnabled(app.app, app.targets, enabled = false)
            settle()
            assertThat(expectMostRecentItem().alert).isNotNull()

            f.viewModel.dismissError()
            settle()

            assertThat(expectMostRecentItem().alert).isNull()
        }
    }

    private fun TestScope.fixture(savedStateHandle: SavedStateHandle = SavedStateHandle()) = DebloaterFixture(backgroundScope, main.testDispatcher, savedStateHandle)

    private fun TestScope.settle() {
        advanceTimeBy(500)
        runCurrent()
    }

    private fun DebloaterUiState.apps() = (content as Result.Success).data

    private fun DebloaterUiState.names() = apps().flatMap { app -> app.targets.map { it.key.id.simpleName } }
}
