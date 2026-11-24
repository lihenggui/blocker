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
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.cache.asEntity
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.rule.GeneralRuleMatcher
import com.merxury.blocker.core.domain.search.ComponentIndex
import com.merxury.blocker.core.domain.search.IndexState
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
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    fun givenRuleMatcherObserving_whenPackagesAreInstalledAndUpdated_thenMatchCountsFollowTheCache() = runTest {
        val fixture = startMonitor()
        val rule = GeneralRule(id = 1, name = "Example", searchKeyword = listOf("Replacement"))
        val rules = TestGeneralRuleRepository().apply { sendRuleList(listOf(rule)) }
        val preferences = TestUserDataRepository().apply { sendUserData(defaultUserData) }
        val matcher = GeneralRuleMatcher(
            fixture.cache,
            apps,
            rules,
            preferences,
            FakeSelectedUserRepository(OWNER),
            SELF_PKG,
            StandardTestDispatcher(testScheduler),
            backgroundScope,
        )

        matcher.matches(OWNER).test {
            assertThat(awaitItem().appCountOf(rule.id)).isEqualTo(0)
            system.install(app, replacement)
            announce(Intent.ACTION_PACKAGE_ADDED)
            assertThat(awaitItem().appCountOf(rule.id)).isEqualTo(1)

            system.install(installedApp(PKG, lastUpdateTime = 3_000), main)
            announce(Intent.ACTION_PACKAGE_REPLACED)
            assertThat(awaitItem().appCountOf(rule.id)).isEqualTo(0)
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
        assertThat(pmBlocks.of(OWNER)).containsExactly(recorded)
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
        val declarations = List(2 * ComponentBatchRunner.BATCH_LIMIT + 1) { declaration("Part$it") }
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
        val repository = object : ComponentStatusRepository by fixture.cache {
            override fun control(request: ControlRequest) = flow {
                // Leave time between requests, when only the batch itself marks the package active.
                if (chunks > 0) {
                    delay(800)
                    assertThat(system.reads("app ")).isEqualTo(1)
                }
                emitAll(fixture.cache.control(request))
            }
        }
        val runner = ComponentBatchRunner(repository, backgroundScope, controlActivity)
        val run = checkNotNull(runner.reserve(BatchKey.App(app.key), ControlGoal.BLOCK, declarations.map { it.id }))
        runner.start(run, declarations, setOf(ControlLayer.PACKAGE_MANAGER))
        run.outcomes.await()
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
        assertThat(pmBlocks.of(OWNER)).containsExactly(recorded)
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
    fun givenOnlyAnotherUserCached_whenStartingWithoutAccess_thenAddOwnUserAndKeepTheCache() = runTest {
        val workApp = installedApp(PKG, WORK)
        dao.upsertUser(deviceUser(WORK).asEntity())
        dao.upsertApp(workApp.asEntity())
        system.users.removeAll { it.id != OWNER }
        channel.setState(noAccess)
        system.install(app, main)

        startMonitor()

        assertThat(apps.app(app.key).first()).isEqualTo(app)
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
        val fixture = startMonitor(cache)

        assertThat(system.log).doesNotContain("revisions ${WORK.value}")
        assertThat(apps.app(workApp.key).first()).isEqualTo(workApp)

        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        system.beforeRevisionsRead = { user ->
            if (user == WORK) {
                channel.setState(noAccess)
                system.appsFailures[WORK] = ReadFailure.UserNotReachable(WORK)
            }
        }
        advanceTimeBy(30_000)
        fixture.monitor.onForeground()
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
        val index = ComponentIndex(cache, channel, backgroundScope)
        index.ensureIndexed(OWNER)
        reading.await()

        val monitor = createMonitor(cache)
        monitor.start()
        monitor.onForeground()
        runCurrent()
        assertThat(system.reads("revisions ")).isEqualTo(0)

        release.complete(Unit)
        index.state(OWNER).first { it is IndexState.Indexed }
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
        val monitor = createMonitor(cache)
        monitor.start()
        reading.await()
        val index = ComponentIndex(cache, channel, backgroundScope)
        index.ensureIndexed(OWNER)
        monitor.onForeground()
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.reads("apps ")).isEqualTo(0)

        release.complete(Unit)
        index.state(OWNER).first { it is IndexState.Indexed }
        settle()
        assertThat(system.reads("revisions ")).isEqualTo(1)
        assertThat(system.reads("apps ")).isEqualTo(1)
    }

    @Test
    fun givenReadsThrow_whenMoreEventsArrive_thenBothMonitorsKeepWorking() = runTest {
        system.install(app, main)
        system.beforeRevisionsRead = { error("Cannot list packages") }
        val fixture = startMonitor()

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
        val monitor = createMonitor(components(backgroundScope))
        monitor.start()
        monitor.start()
        runCurrent()
        apps.apps(OWNER).first()

        val receivers = shadowOf(context.applicationContext as Application).registeredReceivers
        assertThat(receivers.count { it.intentFilter.hasAction(Intent.ACTION_PACKAGE_REPLACED) }).isEqualTo(1)
        assertThat(system.reads("revisions ")).isEqualTo(1)
    }

    private fun TestScope.createMonitor(cache: LocalComponentStatusRepository) = PackageChangeMonitor(
        context,
        cache,
        FakeSelectedUserRepository(OWNER),
        controlActivity,
        backgroundScope,
        StandardTestDispatcher(testScheduler),
        { testScheduler.currentTime },
    )

    private fun TestScope.startMonitor(cache: LocalComponentStatusRepository = components(backgroundScope)): Fixture {
        val monitor = createMonitor(cache)
        monitor.start()
        runCurrent()
        return Fixture(cache, monitor)
    }

    private fun announce(action: String, replacing: Boolean = false) {
        context.sendBroadcast(Intent(action, Uri.fromParts("package", PKG, null)).putExtra(Intent.EXTRA_REPLACING, replacing))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun TestScope.settle() {
        advanceTimeBy(501)
        runCurrent()
    }

    private data class Fixture(val cache: LocalComponentStatusRepository, val monitor: PackageChangeMonitor)
}
