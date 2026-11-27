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

package com.merxury.blocker.core.rule.backup

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakePmBlockRecordSeeder
import com.merxury.blocker.core.testing.repository.device.fakeOperationRecorder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MatAndResetTest {
    private val installedApps = FakeInstalledAppRepository()
    private val components = FakeComponentStatusRepository()
    private val userData = TestUserDataRepository()
    private val channel = FakePrivilegedChannel()
    private val mat = MatRuleImporter(installedApps, components, channel, userData, fakeOperationRecorder())
    private val reset = PackageManagerReset(components, channel, FakePmBlockRecordSeeder(), fakeOperationRecorder())

    @Test
    fun givenProtectedComponent_whenImportingMat_thenItCountsAsSkippedAndWorkSucceeds() = runTest {
        userData.setControllerType(ControllerType.PM)
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT)))
        components.controlEvents = { request ->
            val key = ComponentKey(BOOT.id, request.user)
            listOf(
                ControlEvent.PackageDone(
                    APP,
                    listOf(
                        ComponentOutcome(
                            key,
                            status(BOOT).state,
                            ControlResult.SKIPPED,
                            mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.ProtectedComponent),
                        ),
                    ),
                ),
                ControlEvent.Finished(0, 0, 0, skipped = 1),
            )
        }

        val result = mat.import(listOf(BOOT.id.flatten()), OWNER)

        assertThat(result.summary.skipped).containsExactly(SkipReason.PROTECTED_COMPONENT, 1)
        assertThat(result.summary.failed).isEmpty()
        assertThat(result.summary.changedPackages).isEmpty()
    }

    @Test
    fun givenMatLines_whenImporting_thenDeclaredComponentsAreBlockedAndTheRestCounted() = runTest {
        userData.setControllerType(ControllerType.IFW_PLUS_PM)
        installedApps.sendApps(listOf(installedApp(), installedApp("com.system", isSystem = true)))
        components.sendComponents(OWNER, listOf(status(BOOT), status(SYNC), status(declaration("Sync", SYNC.type, "com.system"))))

        val result = mat.import(
            listOf(
                "$APP/.BootReceiver",
                "$APP/$APP.SyncService disabled",
                " $APP/.Leading",
                "$APP see/https",
                "/.NoPackage",
                "$APP/.Undeclared",
                "com.gone/.Main",
                "com.system/.Sync",
            ),
            OWNER,
        )

        assertThat(components.requests).containsExactly(
            ControlRequest(OWNER, listOf(BOOT, SYNC), ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)),
        )
        assertThat(result.summary.applied).isEqualTo(2)
        assertThat(result.summary.skipped).containsExactly(
            SkipReason.MALFORMED_LINE,
            3,
            SkipReason.NOT_DECLARED,
            1,
            SkipReason.APP_NOT_INSTALLED,
            1,
            SkipReason.SYSTEM_APP,
            1,
        )
    }

    @Test
    fun givenControllerTypeChangedAfterEnqueueing_whenImporting_thenTheTypeAtRunTimeIsUsed() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT)))
        userData.setControllerType(ControllerType.IFW)
        userData.setControllerType(ControllerType.SHIZUKU)

        mat.import(listOf("$APP/.BootReceiver"), OWNER)

        assertThat(components.requests.single().layers).containsExactly(ControlLayer.PACKAGE_MANAGER)
    }

    @Test
    fun givenPmBlocksWrittenByMat_whenResetting_thenTheRecordedComponentsReturnToDefault() = runTest {
        userData.setControllerType(ControllerType.PM)
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT)))
        val key = ComponentKey(BOOT.id, OWNER)

        assertThat((mat.import(listOf(BOOT.id.flatten()), OWNER) as RuleRunResult.Completed).summary.applied).isEqualTo(1)
        assertThat(components.component(key).first()?.state?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat((reset.reset(OWNER) as RuleRunResult.Completed).summary.applied).isEqualTo(1)
        assertThat(components.component(key).first()?.state?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(components.recorded).isEmpty()
    }

    @Test
    fun givenShellRefusal_whenResettingPm_thenItCountsAsMissingPrivilegeAndTheRecordStays() = runTest {
        assertRefusedReset(PrivilegedIdentity.SHELL, FailureReason.NO_PRIVILEGE, keepRefusedRecord = true)
    }

    @Test
    fun givenRootRefusal_whenResettingPm_thenItCountsAsSystemErrorAndTheRecordIsForgotten() = runTest {
        assertRefusedReset(PrivilegedIdentity.ROOT, FailureReason.SYSTEM_ERROR, keepRefusedRecord = false)
    }

    @Test
    fun givenSystemRefusal_whenResettingPm_thenItCountsAsSystemErrorAndTheRecordIsForgotten() = runTest {
        assertRefusedReset(PrivilegedIdentity.SYSTEM, FailureReason.SYSTEM_ERROR, keepRefusedRecord = false)
    }

    private suspend fun assertRefusedReset(identity: PrivilegedIdentity, reason: FailureReason, keepRefusedRecord: Boolean) {
        channel.connectResult = ChannelState.Ready(identity)
        val other = declaration("Tracker", SYNC.type, "com.other")
        val locked = declaration("Locked", SYNC.type, "com.other")
        components.sendComponents(
            OWNER,
            listOf(status(BOOT, pm = PmSetting.DISABLED), status(SYNC), status(other, pm = PmSetting.DISABLED), status(locked, pm = PmSetting.DISABLED)),
        )
        components.sendComponents(WORK, listOf(status(BOOT, pm = PmSetting.DISABLED)))
        components.recorded += listOf(BOOT, other, locked).map { ComponentKey(it.id, OWNER) } + ComponentKey(BOOT.id, WORK)
        components.controlEvents = { request ->
            val outcomes = request.components.map { declaration ->
                val key = ComponentKey(declaration.id, request.user)
                if (declaration == locked) {
                    ComponentOutcome(key, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.FAILED, mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("protected", identity)))
                } else {
                    ComponentOutcome(key, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap())
                }
            }
            listOf(ControlEvent.PackageDone(request.components.first().id.packageName, outcomes))
        }

        val result = reset.reset(OWNER)

        assertThat(components.requests).containsExactly(
            ControlRequest(OWNER, listOf(BOOT), ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER)),
            ControlRequest(OWNER, listOf(locked, other), ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER)),
        ).inOrder()
        assertThat(result.summary.applied).isEqualTo(2)
        assertThat(result.summary.failed).containsExactly(reason, 1)
        assertThat(result.summary.changedPackages).containsExactly(APP, "com.other")
        val remaining = listOfNotNull(ComponentKey(BOOT.id, WORK), ComponentKey(locked.id, OWNER).takeIf { keepRefusedRecord })
        assertThat(components.recorded).containsExactlyElementsIn(remaining)
    }

    @Test
    fun givenComponentsTheAppsDisabledThemselves_whenResettingPm_thenOnlyBlockersRecordedOnesAreUnblocked() = runTest {
        val selfOff = declaration("SelfOff", SYNC.type)
        val otherSelfOff = declaration("SelfOff", SYNC.type, "com.other")
        components.sendComponents(OWNER, listOf(status(BOOT, pm = PmSetting.DISABLED), status(selfOff, pm = PmSetting.DISABLED), status(otherSelfOff, pm = PmSetting.DISABLED)))
        components.recorded += ComponentKey(BOOT.id, OWNER)

        val result = reset.reset(OWNER)

        assertThat(components.requests).containsExactly(ControlRequest(OWNER, listOf(BOOT), ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER)))
        assertThat(result.summary.applied).isEqualTo(1)
        assertThat(components.allComponents(OWNER).first().map { it.declaration to it.state.pm }).containsExactly(
            BOOT to PmSetting.DEFAULT,
            selfOff to PmSetting.DISABLED,
            otherSelfOff to PmSetting.DISABLED,
        )
    }

    @Test
    fun givenRecordsOfAnAppNoLongerInstalled_whenResettingPm_thenTheyCountAsSkippedComponents() = runTest {
        components.sendComponents(OWNER, listOf(status(BOOT, pm = PmSetting.DISABLED)))
        components.recorded += listOf(ComponentKey(BOOT.id, OWNER), ComponentKey(declaration("A", SYNC.type, "com.gone").id, OWNER), ComponentKey(declaration("B", SYNC.type, "com.gone").id, OWNER))

        val result = reset.reset(OWNER)

        assertThat(result.summary.applied).isEqualTo(1)
        assertThat(result.summary.skipped).containsExactly(SkipReason.APP_NOT_INSTALLED, 2)
        assertThat(components.recorded).isEmpty()
    }

    @Test
    fun givenOneAppUnreadable_whenResettingPm_thenTheOthersAreResetAndItIsCounted() = runTest {
        val other = declaration("Tracker", SYNC.type, "com.other")
        components.sendComponents(OWNER, listOf(status(BOOT, pm = PmSetting.DISABLED), status(other, pm = PmSetting.DISABLED)))
        components.recorded += listOf(ComponentKey(BOOT.id, OWNER), ComponentKey(other.id, OWNER))
        components.appRefreshFailures["com.other"] = ReadFailure.ChannelUnavailable

        val result = reset.reset(OWNER)

        assertThat(components.requests.single().components).containsExactly(BOOT)
        assertThat(result.summary.applied).isEqualTo(1)
        assertThat(result.summary.failed).containsExactly(FailureReason.STATE_UNREADABLE, 1)
    }

    @Test
    fun givenManifestDisabledComponent_whenResettingPm_thenItGoesBackToDefault() = runTest {
        val shippedOff = declaration("Off", SYNC.type, enabledInManifest = false)
        components.sendComponents(OWNER, listOf(status(shippedOff, pm = PmSetting.DISABLED)))
        components.recorded += ComponentKey(shippedOff.id, OWNER)

        reset.reset(OWNER)

        assertThat(components.requests.single().goal).isEqualTo(ControlGoal.UNBLOCK)
        assertThat(components.allComponents(OWNER).first().single().state.pm).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenNoChannel_whenImportingMatOrResettingPm_thenTheRunFailsForPrivilege() = runTest {
        channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        assertThat(mat.import(listOf("$APP/.BootReceiver"), OWNER)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
        assertThat(reset.reset(OWNER)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
        assertThat(components.requests).isEmpty()
    }

    @Test
    fun givenSettingsCannotBeRead_whenResettingPm_thenTheRunFails() = runTest {
        components.refreshFailure = ReadFailure.UserNotReachable(WORK)

        assertThat(reset.reset(WORK)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
    }
}
