// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.domain.rule.installedApp
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.ManifestApplication
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.testing.repository.device.FakeAppManifestReader
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePotentialWakeEntriesUseCaseTest {
    @Test fun givenABootReceiver_whenStateChanges_thenReflectsManualBlocksAndVerifiedReEnablingWithoutRescanning() = runTest {
        val f = Fixture(this)
        f.sendStatus(PmSetting.DEFAULT)

        f.observe(TEST_APP).test {
            val entry = awaitItem().single()
            assertThat(entry.category).isEqualTo(PotentialWakeCategory.BOOT_AUTOSTART)
            val path = entry.state.path
            assertThat(path).isEqualTo(
                WakePath(
                    pathKey = "STATIC|BOOT_AUTOSTART|$TEST_APP|$TEST_APP.BootReceiver",
                    target = TEST_APP,
                    actor = null,
                    trigger = ReviveTrigger.SYSTEM_BROADCAST,
                    hold = null,
                    component = "$TEST_APP.BootReceiver",
                    componentType = ComponentType.RECEIVER,
                    action = BOOT_ACTION,
                    count = 0L,
                    durationNanos = 0L,
                    screenOffNanos = 0L,
                    lastSeenMillis = 0L,
                    afterKill = false,
                    blockMethod = WakeBlockMethod.COMPONENT,
                ),
            )
            assertThat(entry.state).isEqualTo(WakePathState(path, WakePathBlock.NONE, null, false))

            f.sendStatus(PmSetting.DISABLED)
            assertThat(awaitItem().single().state).isEqualTo(WakePathState(path, WakePathBlock.USER, null, false))

            f.wake.manual.blocks.value = listOf(path.blockRow(at = 123))
            assertThat(awaitItem().single().state).isEqualTo(WakePathState(path, WakePathBlock.USER, 123, false))

            f.sendStatus(PmSetting.UNKNOWN)
            assertThat(awaitItem().single().state).isEqualTo(WakePathState(path, WakePathBlock.NONE, null, false))

            f.sendStatus(PmSetting.ENABLED)
            assertThat(awaitItem().single().state).isEqualTo(WakePathState(path, WakePathBlock.NONE, null, false, reEnabledByApp = true))
            assertThat(f.manifests.reads).containsExactly(f.app)
        }
    }

    @Test fun givenOwnershipAndFirewallState_whenEitherChanges_thenAutomaticAndUserBlocksAreDistinguished() = runTest {
        val f = Fixture(this)
        f.sendStatus(PmSetting.DISABLED)
        f.wake.automatic.ownedComponents.value = setOf(f.receiver.id)

        f.observe(TEST_APP).test {
            assertThat(awaitItem().single().state.block).isEqualTo(WakePathBlock.AUTOMATIC)

            f.sendStatus(PmSetting.DISABLED, IfwBlock.BLOCKED)
            assertThat(awaitItem().single().state.block).isEqualTo(WakePathBlock.USER)

            f.sendStatus(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
            assertThat(awaitItem().single().state.block).isEqualTo(WakePathBlock.NONE)
        }
    }

    @Test fun givenExcludedReceiver_whenObserving_thenOnlyOtherComponentsRemain() = runTest {
        val f = Fixture(this)
        val manifest = f.manifests.manifests.getValue(f.app)
        f.manifests.manifests[f.app] = manifest.copy(
            application = manifest.application.copy(
                services = listOf(
                    ManifestService(".Push", intentFilters = listOf(ManifestIntentFilter(actions = listOf("com.google.firebase.MESSAGING_EVENT")))),
                    ManifestService(".Job", permission = "android.permission.BIND_JOB_SERVICE"),
                    ManifestService(".Sync", intentFilters = listOf(ManifestIntentFilter(actions = listOf("android.content.SyncAdapter")))),
                ),
                receivers = manifest.application.receivers + ManifestReceiver(
                    ".Present",
                    intentFilters = listOf(ManifestIntentFilter(actions = listOf("android.intent.action.USER_PRESENT"))),
                ),
            ),
        )

        val paths = f.observe(TEST_APP, excludeComponents = setOf(f.receiver.id.className)).first().map { it.state.path }

        assertThat(paths.map { it.component to it.trigger }).containsExactly(
            "$TEST_APP.Push" to ReviveTrigger.PUSH_TRANSPORT,
            "$TEST_APP.Job" to ReviveTrigger.JOB,
            "$TEST_APP.Sync" to ReviveTrigger.SYNC,
            "$TEST_APP.Present" to ReviveTrigger.SYSTEM_BROADCAST,
        ).inOrder()
        assertThat(paths[1].action).isNull()
    }

    @Test fun givenADefaultDisabledReceiver_whenItIsEnabledOrExplicitlyBlocked_thenItRemainsVisible() = runTest {
        val f = Fixture(this, enabled = false)
        f.sendStatus(PmSetting.DEFAULT)

        f.observe(TEST_APP).test {
            assertThat(awaitItem()).isEmpty()

            f.sendStatus(PmSetting.ENABLED)
            val state = awaitItem().single().state
            assertThat(state.block).isEqualTo(WakePathBlock.NONE)

            f.sendStatus(PmSetting.DISABLED)
            assertThat(awaitItem().single().state.block).isEqualTo(WakePathBlock.USER)

            f.sendStatus(PmSetting.DEFAULT)
            assertThat(awaitItem()).isEmpty()

            f.wake.manual.blocks.value = listOf(state.path.blockRow(at = 123))
            assertThat(awaitItem().single().state.blockedAtMillis).isEqualTo(123)

            f.wake.manual.blocks.value = emptyList()
            assertThat(awaitItem()).isEmpty()

            f.wake.automatic.ownedComponents.value = setOf(f.receiver.id)
            assertThat(awaitItem().single().state.block).isEqualTo(WakePathBlock.AUTOMATIC)

            f.wake.automatic.ownedComponents.value = emptySet()
            assertThat(awaitItem()).isEmpty()

            f.sendStatus(PmSetting.DEFAULT, IfwBlock.BLOCKED)
            assertThat(awaitItem().single().state.block).isEqualTo(WakePathBlock.USER)
        }
    }

    @Test fun givenAProtectedPackage_whenObserving_thenEmitsEmptyWithoutReadingItsManifest() = runTest {
        val f = Fixture(this)
        f.wake.components.protectedApp = f.app

        assertThat(f.observe(TEST_APP).first()).isEmpty()
        assertThat(f.manifests.reads).isEmpty()
    }

    @Test fun givenNoReadableManifest_whenObserving_thenEmitsEmpty() = runTest {
        val f = Fixture(this)
        f.manifests.manifests.clear()

        assertThat(f.observe(TEST_APP).first()).isEmpty()
        assertThat(f.manifests.reads).containsExactly(f.app)
    }

    @Test fun givenAStaticPathWithNoWakeHistory_whenBlockingAndRestoring_thenExistingUseCasesManageItsSavedBlock() = runTest {
        val f = Fixture(this)
        val wake = f.wake
        val setBlocked = SetWakePathsBlockedUseCase(wake.manual, wake.automatic, wake.background)
        val restore = RestoreAppWakeChangesUseCase(wake.activity, wake.manual, wake.automatic, wake.background, wake.restriction)
        val unrelated = finding("unrelated", target = TEST_OTHER).toWakePath().blockRow()
        wake.manual.blocks.value = listOf(unrelated)
        f.sendStatus(PmSetting.DEFAULT)
        val state = f.observe(TEST_APP).first().single().state
        val expected = WakeBlockOutcome(setOf(state.path.pathKey), null)

        assertThat(setBlocked(listOf(state), blocked = true)).isEqualTo(expected)
        assertThat(wake.manual.requests).containsExactly(state.path to WakeBlockMethod.COMPONENT)
        f.sendStatus(PmSetting.DISABLED)
        val blocked = f.observe(TEST_APP).first().single().state
        assertThat(blocked.block).isEqualTo(WakePathBlock.USER)
        assertThat(blocked.blockedAtMillis).isEqualTo(100)

        assertThat(setBlocked(listOf(blocked), blocked = false)).isEqualTo(expected)
        assertThat(wake.manual.unblockedPaths).containsExactly(state.path)
        assertThat(wake.manual.blocks.value).containsExactly(unrelated)

        f.sendStatus(PmSetting.DEFAULT)
        assertThat(setBlocked(listOf(state), blocked = true)).isEqualTo(expected)
        val restored = restore(TEST_APP)
        assertThat(restored.outcome).isEqualTo(expected)
        assertThat(wake.manual.blocks.value).containsExactly(unrelated)
        f.sendStatus(PmSetting.DEFAULT)
        assertThat(f.observe(TEST_APP).first().single().state).isEqualTo(state)

        assertThat(restore.undo(requireNotNull(restored.undo))).isEqualTo(expected)
        assertThat(wake.manual.blocks.value.map { it.pathKey }).containsExactly(unrelated.pathKey, state.path.pathKey)
    }

    @Test fun givenAppUpdates_whenObserving_thenOnlyNewApkRevisionsRescanTheManifest() = runTest {
        val f = Fixture(this)
        val installed = installedApp(TEST_APP)
        f.apps.sendApps(listOf(installed))

        f.observe(TEST_APP).test {
            assertThat(awaitItem().single().state.path.component).isEqualTo("$TEST_APP.BootReceiver")
            f.apps.sendApps(listOf(installed.copy(label = "Renamed")))
            f.manifests.manifests[f.app] = AndroidManifest(
                TEST_APP,
                application = ManifestApplication(
                    receivers = listOf(ManifestReceiver(".NewReceiver", intentFilters = listOf(ManifestIntentFilter(actions = listOf(BOOT_ACTION))))),
                ),
            )
            f.apps.sendApps(listOf(installed.copy(versionCode = installed.versionCode + 1)))
            assertThat(awaitItem().single().state.path.component).isEqualTo("$TEST_APP.NewReceiver")
            assertThat(f.manifests.reads).containsExactly(f.app, f.app)
        }
    }

    private class Fixture(scope: TestScope, enabled: Boolean = true) {
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        val receiver = receiver(TEST_APP, "BootReceiver").copy(enabledInManifest = enabled)
        val wake = WakeStateTestFixture(scope)
        val manifests = FakeAppManifestReader().apply {
            val boot = ManifestReceiver(".BootReceiver", enabled = enabled, intentFilters = listOf(ManifestIntentFilter(actions = listOf(BOOT_ACTION))))
            manifests[app] = AndroidManifest(TEST_APP, application = ManifestApplication(receivers = listOf(boot)))
        }
        val apps = FakeInstalledAppRepository()
        val observe = ObservePotentialWakeEntriesUseCase(manifests, apps, wake.components, wake.manual, wake.automatic, StandardTestDispatcher(scope.testScheduler))

        fun sendStatus(pm: PmSetting, ifw: IfwBlock = IfwBlock.NOT_BLOCKED) {
            wake.components.sendComponents(UserId.SYSTEM, listOf(ComponentStatus(receiver, ControlState(pm, ifw))))
        }
    }
}

private const val BOOT_ACTION = "android.intent.action.BOOT_COMPLETED"
