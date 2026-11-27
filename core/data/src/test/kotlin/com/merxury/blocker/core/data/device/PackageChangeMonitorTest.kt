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

package com.merxury.blocker.core.data.device

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.cache.asEntity
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PackageChangeMonitorTest : DeviceRepositoryTestBase(Runnable::run) {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val app = installedApp(PKG)
    private val main = declaration("Main")
    private val replacement = declaration("Replacement")
    private val noAccess = ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)

    @Test
    fun givenRunningMonitor_whenPackageAdded_thenAppAndComponentsAppearInCacheAndSearch() = runTest {
        val fixture = startMonitor()
        system.install(app, main)
        announce(Intent.ACTION_PACKAGE_ADDED)
        settle()

        assertThat(apps.apps(OWNER).first()).containsExactly(app)
        assertThat(apps.search(OWNER, app.label).first()).containsExactly(app)
        assertThat(fixture.cache.search(OWNER, "Main").first().single().declaration).isEqualTo(main)
    }

    @Test
    fun givenCacheObserved_whenPackageIsInstalledAndUpdated_thenComponentListFollowsBroadcasts() = runTest {
        val fixture = startMonitor()

        turbineScope {
            val packageComponents = fixture.cache.components(app.key).testIn(backgroundScope)
            val allComponents = fixture.cache.allComponents(OWNER).testIn(backgroundScope)
            assertThat(packageComponents.awaitItem()).isEmpty()
            assertThat(allComponents.awaitItem()).isEmpty()

            system.install(app, replacement)
            announce(Intent.ACTION_PACKAGE_ADDED)
            assertThat(packageComponents.awaitItem().map { it.declaration }).containsExactly(replacement)
            assertThat(allComponents.awaitItem().map { it.declaration }).containsExactly(replacement)

            system.install(installedApp(PKG, lastUpdateTime = 3_000), main)
            announce(Intent.ACTION_PACKAGE_REPLACED)
            assertThat(packageComponents.awaitItem().map { it.declaration }).containsExactly(main)
            assertThat(allComponents.awaitItem().map { it.declaration }).containsExactly(main)

            packageComponents.cancelAndIgnoreRemainingEvents()
            allComponents.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAppCached_whenReplacingRemovalThenAddedAndReplaced_thenRefreshOneRevision() = runTest {
        system.install(app, main)
        val fixture = startMonitor()
        pmBlocks.record(listOf(ComponentKey(main.id, OWNER)))

        announce(Intent.ACTION_PACKAGE_REMOVED, replacing = true)
        settle()
        assertThat(apps.app(app.key).first()).isEqualTo(app)
        assertThat(pmBlocks.of(OWNER)).containsExactly(ComponentKey(main.id, OWNER))
        assertThat(system.reads("app ")).isEqualTo(1)

        val updated = installedApp(PKG, lastUpdateTime = 3_000).copy(versionCode = 2, apkPath = "/updated/base.apk")
        system.install(updated, replacement)
        announce(Intent.ACTION_PACKAGE_ADDED, replacing = true)
        announce(Intent.ACTION_PACKAGE_REPLACED)
        settle()

        assertThat(system.reads("app ")).isEqualTo(2)
        assertThat(apps.app(app.key).first()).isEqualTo(updated)
        assertThat(fixture.cache.components(app.key).first().single().declaration).isEqualTo(replacement)
    }

    @Test
    fun givenAppCached_whenOnlyReplacedIsReceived_thenDeclarationsAreReplaced() = runTest {
        system.install(app, main)
        val fixture = startMonitor()

        // Also covers version changes with the same timestamp.
        system.install(app.copy(versionCode = 2), replacement)
        announce(Intent.ACTION_PACKAGE_REPLACED)
        settle()

        assertThat(apps.app(app.key).first()?.versionCode).isEqualTo(2)
        assertThat(fixture.cache.components(app.key).first().single().declaration).isEqualTo(replacement)
    }

    @Test
    fun givenSharedAppWithRulesAndRecords_whenRemoved_thenOnlyItsOwnCacheRowsDisappear() = runTest {
        assertCacheOnlyRemoval(Intent.ACTION_PACKAGE_REMOVED)
    }

    @Test
    fun givenSharedAppWithRulesAndRecords_whenFullyRemoved_thenOnlyItsOwnCacheRowsDisappear() = runTest {
        assertCacheOnlyRemoval(Intent.ACTION_PACKAGE_FULLY_REMOVED)
    }

    private suspend fun TestScope.assertCacheOnlyRemoval(action: String) {
        val workApp = installedApp(PKG, WORK)
        system.install(app, main)
        system.install(workApp, main)
        val fixture = startMonitor()
        fixture.cache.refresh(workApp.key)
        store.setBlocked(PKG, mapOf(main to true))
        val before = files.fileContent(PKG)
        val recorded = ComponentKey(main.id, OWNER)
        pmBlocks.record(listOf(recorded))

        system.apps.remove(app.key)
        announce(action)
        settle()

        assertThat(before).isNotNull()
        assertThat(apps.app(app.key).first()).isNull()
        assertThat(fixture.cache.components(app.key).first()).isEmpty()
        assertThat(fixture.cache.components(workApp.key).first().single().declaration).isEqualTo(main)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
        assertThat(files.fileContent(PKG)).isEqualTo(before)
    }

    @Test
    fun givenChangedBurst_whenTheLastBroadcastSettles_thenRefreshAppAndComponentStateOnce() = runTest {
        system.install(app, main)
        val fixture = startMonitor()
        val changed = app.copy(enabled = false, enabledSetting = AppEnabledSetting.DISABLED)
        system.install(changed, main)
        system.put(OWNER, main, PmSetting.DISABLED)

        repeat(5) {
            announce(Intent.ACTION_PACKAGE_CHANGED)
            advanceTimeBy(125)
        }
        assertThat(system.reads("app ")).isEqualTo(1)
        settle()

        assertThat(system.reads("app ")).isEqualTo(2)
        assertThat(system.reads("declarations")).isEqualTo(1)
        assertThat(apps.app(app.key).first()).isEqualTo(changed)
        assertThat(fixture.cache.components(app.key).first().single().state.pm).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenRefreshInProgress_whenNewChangesArrive_thenExactlyOneFollowUpReadsLatestState() = runTest {
        system.install(app, main)
        val fixture = startMonitor()
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        system.afterPmRead = {
            reading.complete(Unit)
            release.await()
        }
        announce(Intent.ACTION_PACKAGE_CHANGED)
        settle()
        reading.await()

        system.put(OWNER, main, PmSetting.DISABLED)
        repeat(5) { announce(Intent.ACTION_PACKAGE_CHANGED) }
        settle()
        assertThat(system.reads("app ")).isEqualTo(2)

        release.complete(Unit)
        settle()
        assertThat(system.reads("app ")).isEqualTo(3)
        assertThat(fixture.cache.components(app.key).first().single().state.pm).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenBlockerBatchSpansSeveralChunks_whenEveryChunkBroadcasts_thenRefreshOnceAfterAllChunks() = runTest {
        val declarations = List(5) { declaration("Part$it") }
        system.install(app, *declarations.toTypedArray())
        val fixture = startMonitor()
        var chunks = 0
        componentControl.apply = { request ->
            flow {
                chunks++
                request.components.forEach { system.put(OWNER, it, PmSetting.DISABLED) }
                announce(Intent.ACTION_PACKAGE_CHANGED)
                delay(800)
                assertThat(system.reads("app ")).isEqualTo(1)
                val outcomes = request.components.map {
                    ComponentOutcome(ComponentKey(it.id, request.user), ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, failures = emptyMap())
                }
                emit(ControlEvent.PackageDone(PKG, outcomes))
                emit(ControlEvent.Finished(achieved = outcomes.size, partial = 0, failed = 0))
            }
        }
        controlActivity.duringControl(setOf(PKG)) {
            declarations.chunked(2).forEach { chunk ->
                fixture.cache.control(ControlRequest(OWNER, chunk, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER)), operation = operation()).toList()
                // Leave time between requests, when only the outer control marks the package active.
                delay(800)
                assertThat(system.reads("app ")).isEqualTo(1)
            }
        }
        settle()

        assertThat(chunks).isEqualTo(3)
        assertThat(system.reads("app ")).isEqualTo(2)
        assertThat(fixture.cache.components(app.key).first().map { it.state.pm }.toSet()).containsExactly(PmSetting.DISABLED)
    }

    @Test
    fun givenStaleCache_whenForegroundAfterThrottle_thenReconcileNewUpdatedRemovedAndUnchangedApps() = runTest {
        val gone = installedApp("example.gone")
        val unchanged = installedApp("example.unchanged")
        val added = installedApp("example.added")
        val updated = installedApp(PKG, lastUpdateTime = 3_000)
        system.install(app, main)
        system.install(gone)
        system.install(unchanged)
        val fixture = startMonitor()
        val recorded = ComponentKey(declaration("Gone", packageName = gone.key.packageName).id, OWNER)
        pmBlocks.record(listOf(recorded))
        system.log.clear()
        system.install(updated, replacement)
        system.install(added)
        system.apps.remove(gone.key)

        fixture.monitor.onForeground()
        runCurrent()
        assertThat(system.log).isEmpty()

        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
        settle()

        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.log.filter { it.startsWith("app ") }).containsExactly("app ${updated.key}", "app ${added.key}", "app ${gone.key}")
        assertThat(apps.apps(OWNER).first()).containsExactly(updated, added, unchanged)
        assertThat(fixture.cache.components(app.key).first().single().declaration).isEqualTo(replacement)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
    }

    @Test
    fun givenAnotherUserCached_whenStartHasAccess_thenReconcileAlsoThatUser() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(workApp, main)
        val fixture = components(backgroundScope)
        fixture.refresh(workApp.key)
        val updated = installedApp(PKG, WORK, lastUpdateTime = 3_000)
        system.install(updated, replacement)

        startMonitor(fixture)

        assertThat(apps.app(workApp.key).first()).isEqualTo(updated)
        assertThat(fixture.components(workApp.key).first().single().declaration).isEqualTo(replacement)
        assertThat(system.log).contains("revisions ${WORK.value}")
    }

    @Test
    fun givenOnlyAnotherUserCached_whenStartingWithoutAccess_thenSkipBothUsers() = runTest {
        val workApp = installedApp(PKG, WORK)
        dao.upsertUser(deviceUser(WORK).asEntity())
        dao.upsertApp(workApp.asEntity())
        system.users.removeAll { it.id != OWNER }
        channel.setState(noAccess)
        system.install(app, main)

        startMonitor(components(backgroundScope))

        assertThat(apps.app(app.key).first()).isNull()
        assertThat(apps.app(workApp.key).first()).isEqualTo(workApp)
        assertThat(system.log).doesNotContain("revisions ${WORK.value}")
    }

    @Test
    fun givenAnotherUserCached_whenAccessIsMissingOrLost_thenKeepItsFullCache() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(workApp, main)
        val cache = components(backgroundScope)
        cache.refresh(workApp.key)
        channel.setState(noAccess)
        system.apps.remove(workApp.key)
        startMonitor(cache)

        assertThat(system.log).doesNotContain("revisions ${WORK.value}")
        assertThat(apps.app(workApp.key).first()).isEqualTo(workApp)

        system.beforeRevisionsRead = { user ->
            if (user == WORK) {
                channel.setState(noAccess)
                system.appsFailures[WORK] = ReadFailure.UserNotReachable(WORK)
            }
        }
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        settle()

        assertThat(system.log).contains("revisions ${WORK.value}")
        assertThat(apps.app(workApp.key).first()).isEqualTo(workApp)
        assertThat(cache.components(workApp.key).first().single().declaration).isEqualTo(main)
    }

    @Test
    fun givenAnotherUserReadLosesAccessMidRefresh_whenReconciling_thenKeepItsKnownComponentState() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(workApp, main)
        system.put(WORK, main, PmSetting.DISABLED)
        val cache = components(backgroundScope)
        cache.refresh(workApp.key)
        system.install(installedApp(PKG, WORK, lastUpdateTime = 3_000), replacement)
        system.pmFailures[PKG] = ReadFailure.UserNotReachable(WORK)

        startMonitor(cache)

        assertThat(apps.app(workApp.key).first()).isEqualTo(workApp)
        val component = cache.components(workApp.key).first().single()
        assertThat(component.declaration).isEqualTo(main)
        assertThat(component.state.pm).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenFullIndexInProgress_whenForeground_thenReconciliationWaitsForTheIndex() = runTest {
        system.install(app, main)
        val cache = components(backgroundScope)
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        system.afterPmRead = {
            reading.complete(Unit)
            release.await()
        }
        val index = backgroundScope.async { cache.refreshAll(OWNER).first { it is RefreshProgress.Finished } }
        reading.await()

        val monitor = createMonitor(cache)
        monitor.start()
        monitor.onForeground()
        runCurrent()
        assertThat(system.reads("revisions ")).isEqualTo(0)

        release.complete(Unit)
        index.await()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.reads("pm ")).isEqualTo(1)
    }

    @Test
    fun givenReconciliationRunning_whenIndexAndForegroundAreRequested_thenNoScanOverlaps() = runTest {
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        system.beforeRevisionsRead = {
            reading.complete(Unit)
            release.await()
        }
        val cache = components(backgroundScope)
        system.install(app, main)
        cache.refresh(app.key)
        val monitor = createMonitor(cache)
        monitor.start()
        reading.await()
        val index = backgroundScope.async { cache.refreshAll(OWNER).first { it is RefreshProgress.Finished } }
        monitor.onForeground()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.reads("apps ")).isEqualTo(0)

        release.complete(Unit)
        index.await()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.reads("apps ")).isEqualTo(1)
    }

    @Test
    fun givenReadsThrow_whenMoreEventsArrive_thenBothMonitorsKeepWorking() = runTest {
        system.install(app, main)
        system.beforeRevisionsRead = { error("Cannot list packages") }
        val cache = components(backgroundScope)
        apps.refreshApp(app.key)
        val fixture = startMonitor(cache)

        system.afterPmRead = { error("Cannot read settings") }
        announce(Intent.ACTION_PACKAGE_CHANGED)
        settle()
        assertThat(fixture.cache.components(app.key).first()).isEmpty()

        system.afterPmRead = {}
        announce(Intent.ACTION_PACKAGE_CHANGED)
        settle()
        assertThat(fixture.cache.components(app.key).first().single().declaration).isEqualTo(main)

        system.beforeRevisionsRead = {}
        system.apps.remove(app.key)
        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
        settle()
        assertThat(apps.app(app.key).first()).isNull()
    }

    @Test
    fun givenTwoStarts_whenPackageBroadcasts_thenOnlyOneReceiverAndInitialReconciliationRun() = runTest {
        system.install(app, main)
        apps.refreshApp(app.key)
        val monitor = createMonitor(components(backgroundScope))
        monitor.start()
        monitor.start()
        runCurrent()
        apps.apps(OWNER).first()

        val receivers = shadowOf(context.applicationContext as Application).registeredReceivers
        assertThat(receivers.count { it.intentFilter.hasAction(Intent.ACTION_PACKAGE_REPLACED) }).isEqualTo(1)
        assertThat(system.reads("revisions ")).isEqualTo(1)
    }

    @Test
    fun givenCachedProfiles_whenStoppedOrLocked_thenReconcileOnlyAfterUnlock() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(workApp, main)
        val cache = components(backgroundScope)
        cache.refresh(workApp.key)
        system.install(workApp.copy(versionCode = 2), replacement)
        val profile = deviceUser(WORK)
        system.users.removeAll { it.id == WORK }
        system.users += profile.copy(running = false, unlocked = false)

        val fixture = startMonitor(cache)
        assertThat(system.reads("revisions ")).isEqualTo(0)

        system.users.removeAll { it.id == WORK }
        system.users += profile.copy(running = true, unlocked = false)
        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(0)
        assertThat(apps.app(workApp.key).first()).isEqualTo(workApp)

        system.users.removeAll { it.id == WORK }
        system.users += profile.copy(running = true, unlocked = true)
        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(cache.components(workApp.key).first().single().declaration).isEqualTo(replacement)
    }

    @Test
    fun givenTwoUsersAtOldVersion_whenOtaKeepsTimestamp_thenReadDeclarationsOnce() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(app, main)
        system.install(workApp, main)
        val cache = components(backgroundScope)
        cache.refresh(app.key)
        cache.refresh(workApp.key)
        system.log.clear()
        val ownerUpdated = app.copy(versionCode = 2)
        val workUpdated = workApp.copy(versionCode = 2)
        system.install(ownerUpdated, replacement)
        system.install(workUpdated)

        startMonitor(cache)

        assertThat(system.reads("declarations")).isEqualTo(1)
        assertThat(system.reads("pm ")).isEqualTo(2)
        assertThat(apps.app(app.key).first()).isEqualTo(ownerUpdated)
        assertThat(apps.app(workApp.key).first()).isEqualTo(workUpdated)

        // The same invariant applies when the app-list or detail path catches up afterward.
        system.install(ownerUpdated.copy(versionCode = 3), main)
        cache.refresh(app.key)
        dao.upsertApp(workUpdated.copy(versionCode = 3).asEntity())
        assertThat(dao.scanMarker(PKG)).isEqualTo(app.lastUpdateTime)
    }

    @Test
    fun givenLockedFirstReader_whenAnUnlockedUserReconciles_thenReplaceWithTheFullList() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(app, main, replacement)
        system.install(workApp)
        system.put(OWNER, replacement, PmSetting.DISABLED)
        val (cache, monitor) = startMonitor()
        apps.refresh(WORK)
        val record = ComponentKey(replacement.id, OWNER)
        pmBlocks.record(listOf(record))
        runCurrent()
        store.setBlocked(PKG, mapOf(replacement to true))
        runCurrent()
        val before = cache.components(app.key).first()
        val ownerUpdated = installedApp(PKG, lastUpdateTime = 3_000)
        val workUpdated = ownerUpdated.copy(key = workApp.key, uid = workApp.uid)
        val added = declaration("Added")
        system.install(ownerUpdated, main, replacement, added)
        system.install(workUpdated)
        system.declarationsByUser[app.key] = listOf(main)
        system.users.clear()
        system.users += listOf(deviceUser(OWNER).copy(unlocked = false), deviceUser(WORK).copy(unlocked = true))

        announce(Intent.ACTION_PACKAGE_CHANGED)
        settle()

        assertThat(system.reads("declarations")).isEqualTo(2)
        assertThat(dao.scanMarker(PKG)).isEqualTo(app.lastUpdateTime)
        assertThat(apps.app(app.key).first()).isEqualTo(app)
        assertThat(cache.components(app.key).first()).isEqualTo(before)
        assertThat(pmBlocks.of(OWNER)).containsExactly(record)

        // A retry still needs to read declarations after the app list has caught up for both users.
        apps.refresh(OWNER)
        apps.refresh(WORK)
        advanceTimeBy(30_000)
        monitor.onForeground()
        settle()

        assertThat(system.reads("declarations")).isEqualTo(3)
        assertThat(dao.scanMarker(PKG)).isEqualTo(workUpdated.lastUpdateTime)
        assertThat(cache.components(app.key).first().map { it.declaration }).containsExactly(main, replacement, added)
        assertThat(cache.components(workApp.key).first().map { it.declaration }).containsExactly(main, replacement, added)
        assertThat(pmBlocks.of(OWNER)).containsExactly(record)
    }

    @Test
    fun givenUnknownLiveStateAndUnchangedRevision_whenRefreshShrinks_thenReconcileAgainAfterUnlock() = runTest {
        system.install(app, main, replacement)
        val (cache, monitor) = startMonitor()
        val before = cache.components(app.key).first()
        system.install(app, main)
        system.usersFailure = ReadFailure.ChannelUnavailable

        assertThat(cache.refresh(app.key)).isEqualTo(ReadResult.Failure(ReadFailure.ManifestUnreadable(PKG)))
        assertThat(dao.scanMarker(PKG)).isEqualTo(app.lastUpdateTime)
        assertThat(cache.components(app.key).first()).isEqualTo(before)

        system.usersFailure = null
        system.users.removeAll { it.id == OWNER }
        system.users += deviceUser(OWNER).copy(unlocked = true)
        advanceTimeBy(30_000)
        monitor.onForeground()
        settle()

        assertThat(system.reads("declarations")).isEqualTo(3)
        assertThat(cache.components(app.key).first().map { it.declaration }).containsExactly(main)

        advanceTimeBy(30_000)
        monitor.onForeground()
        settle()
        assertThat(system.reads("declarations")).isEqualTo(3)
    }

    @Test
    fun givenSameRevisionAndPmFailure_whenBroadcastAborts_thenNextReconciliationRetries() = runTest {
        system.install(app, main)
        system.put(OWNER, main, PmSetting.DISABLED)
        val fixture = startMonitor()
        val key = ComponentKey(main.id, OWNER)
        pmBlocks.record(listOf(key))
        system.install(app.copy(enabled = false, enabledSetting = AppEnabledSetting.DISABLED))
        system.pmFailures[PKG] = ReadFailure.ChannelUnavailable

        announce(Intent.ACTION_PACKAGE_CHANGED)
        settle()
        assertThat(apps.app(app.key).first()).isEqualTo(app)
        assertThat(fixture.cache.components(app.key).first().single().state.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(pmBlocks.of(OWNER)).containsExactly(key)

        system.pmFailures.clear()
        system.put(OWNER, main, PmSetting.DEFAULT)
        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
        settle()
        assertThat(system.reads("pm ")).isEqualTo(3)
        assertThat(apps.app(app.key).first()?.enabled).isFalse()
        assertThat(fixture.cache.components(app.key).first().single().state.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
    }

    @Test
    fun givenKnownFirewallState_whenRuleReadFails_thenKeepBothLayersAndAppRevision() = runTest {
        system.install(app, main)
        val fixture = startMonitor()
        store.setBlocked(PKG, mapOf(main to true))
        runCurrent()
        store.clearCache()
        files.unreadablePackages += PKG
        system.put(OWNER, main, PmSetting.DISABLED)
        system.install(app.copy(versionCode = 2))

        announce(Intent.ACTION_PACKAGE_REPLACED)
        settle()

        assertThat(apps.app(app.key).first()).isEqualTo(app)
        assertThat(fixture.cache.components(app.key).first().single().state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
    }

    @Test
    fun givenFreshCacheOrAppListOnly_whenReconciling_thenNoIndividualReadsUntilIndex() = runTest {
        repeat(8) { system.install(installedApp("example.app$it"), declaration("Main", packageName = "example.app$it")) }
        val cache = components(backgroundScope)
        val fixture = startMonitor(cache)
        assertThat(system.reads("revisions ")).isEqualTo(0)

        apps.refresh(OWNER)
        system.install(app, main)
        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.reads("app ")).isEqualTo(0)
        assertThat(system.reads("declarations")).isEqualTo(0)
        assertThat(system.reads("pm ")).isEqualTo(0)

        cache.refreshAll(OWNER).toList()
        assertThat(system.reads("declarations")).isEqualTo(9)
        assertThat(system.reads("pm ")).isEqualTo(9)
    }

    @Test
    fun givenSharedDeclarationsFromOwner_whenWorkProfileHasOnlyAppList_thenDoNotIndexNewWorkApps() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(app, main)
        system.install(workApp)
        val cache = components(backgroundScope)
        cache.refresh(app.key)
        apps.refresh(WORK)
        val added = installedApp("example.worknew", WORK)
        system.install(added)
        system.log.clear()

        startMonitor(cache)

        assertThat(system.reads("revisions ")).isEqualTo(2)
        assertThat(system.reads("app ")).isEqualTo(0)
        assertThat(apps.app(added.key).first()).isNull()
    }

    @Test
    fun givenOwnToggle_whenMatchingBroadcastArrivesLate_thenSkipItAndRefreshExternalChanges() = runTest {
        system.install(app, main, replacement)
        val fixture = startMonitor()
        componentControl.apply = { request ->
            flow {
                system.put(OWNER, main, PmSetting.DISABLED)
                val outcome = ComponentOutcome(
                    ComponentKey(main.id, request.user),
                    ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
                    ControlResult.ACHIEVED,
                    emptyMap(),
                )
                emit(ControlEvent.PackageDone(PKG, listOf(outcome), setOf(main.id)))
            }
        }
        fixture.cache.control(ControlRequest(OWNER, listOf(main), ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER)), operation = operation()).toList()
        system.log.clear()

        advanceTimeBy(1_000)
        announce(Intent.ACTION_PACKAGE_CHANGED, components = listOf(main.id.className))
        settle()
        assertThat(system.reads("app ")).isEqualTo(0)

        system.put(OWNER, replacement, PmSetting.DISABLED)
        announce(Intent.ACTION_PACKAGE_CHANGED, components = listOf(main.id.className, replacement.id.className))
        settle()
        assertThat(system.reads("app ")).isEqualTo(1)
        assertThat(fixture.cache.components(app.key).first().map { it.state.pm }).containsExactly(PmSetting.DISABLED, PmSetting.DISABLED)

        announce(Intent.ACTION_PACKAGE_CHANGED, components = listOf(PKG))
        settle()
        assertThat(system.reads("app ")).isEqualTo(2)

        advanceTimeBy(3_000)
        announce(Intent.ACTION_PACKAGE_CHANGED, components = listOf(main.id.className))
        settle()
        assertThat(system.reads("app ")).isEqualTo(3)
    }

    @Test
    fun givenOrphanedRecord_whenReconciliationConfirmsRemoval_thenForgetIt() = runTest {
        val cached = installedApp("example.cached")
        system.install(cached)
        apps.refresh(OWNER)
        val record = ComponentKey(main.id, OWNER)
        pmBlocks.record(listOf(record))

        startMonitor(components(backgroundScope))

        assertThat(apps.app(app.key).first()).isNull()
        assertThat(pmBlocks.of(OWNER)).isEmpty()
        assertThat(system.reads("app ")).isEqualTo(1)
    }

    @Test
    fun givenNoFirewallAccessByDesign_whenPackageChanges_thenKeepKnownFirewallAndUpdatePm() = runTest {
        system.install(app, main)
        val fixture = startMonitor()
        store.setBlocked(PKG, mapOf(main to true))
        runCurrent()
        store.clearCache()
        channel.setState(noAccess)
        files.noAccess = true
        system.put(OWNER, main, PmSetting.DISABLED)

        announce(Intent.ACTION_PACKAGE_CHANGED)
        settle()

        assertThat(fixture.cache.components(app.key).first().single().state).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED))
    }

    @Test
    fun givenThreeHundredBroadcasts_whenRefreshing_thenLimitConcurrentReads() = runTest {
        startMonitor()
        var inFlight = 0
        var highWaterMark = 0
        system.afterPmRead = {
            inFlight++
            highWaterMark = maxOf(highWaterMark, inFlight)
            delay(20)
            inFlight--
        }
        repeat(300) {
            val name = "example.package$it"
            system.install(installedApp(name), declaration("Main", packageName = name))
            announce(Intent.ACTION_PACKAGE_ADDED, packageName = name)
        }

        advanceTimeBy(10_000)

        assertThat(system.reads("pm ")).isEqualTo(300)
        assertThat(apps.apps(OWNER).first()).hasSize(300)
        assertThat(highWaterMark).isEqualTo(AUTOMATIC_REFRESH_PARALLELISM)
        assertThat(inFlight).isEqualTo(0)
    }

    @Test
    fun givenCachedWorkApp_whenChannelBecomesReadyDuringThrottle_thenReconcileImmediately() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(workApp, main)
        val cache = components(backgroundScope)
        cache.refresh(workApp.key)
        channel.setState(noAccess)
        val updated = workApp.copy(versionCode = 2)
        system.install(updated, replacement)

        startMonitor(cache)
        assertThat(system.reads("revisions ")).isEqualTo(0)
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()

        assertThat(testScheduler.currentTime).isEqualTo(0)
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(apps.app(workApp.key).first()).isEqualTo(updated)
        assertThat(cache.components(workApp.key).first().single().declaration).isEqualTo(replacement)
    }

    private fun TestScope.createMonitor(cache: LocalComponentStatusRepository): PackageChangeMonitor {
        elapsedRealtime = { testScheduler.currentTime }
        return PackageChangeMonitor(
            context,
            cache,
            FakeSelectedUserRepository(OWNER),
            controlActivity,
            channel,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            elapsedRealtime,
        )
    }

    private suspend fun TestScope.startMonitor(): Fixture {
        val cache = components(backgroundScope)
        system.apps.keys.filter { it.userId == OWNER }.forEach { cache.refresh(it) }
        return startMonitor(cache)
    }

    private fun TestScope.startMonitor(cache: LocalComponentStatusRepository): Fixture {
        val monitor = createMonitor(cache)
        monitor.start()
        runCurrent()
        return Fixture(cache, monitor)
    }

    private fun announce(action: String, replacing: Boolean = false, packageName: String = PKG, components: List<String>? = null) {
        val intent = Intent(action, Uri.fromParts("package", packageName, null)).putExtra(Intent.EXTRA_REPLACING, replacing)
        components?.let { intent.putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, it.toTypedArray()) }
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun TestScope.settle() {
        advanceTimeBy(501)
        runCurrent()
    }

    private data class Fixture(val cache: LocalComponentStatusRepository, val monitor: PackageChangeMonitor)
}
