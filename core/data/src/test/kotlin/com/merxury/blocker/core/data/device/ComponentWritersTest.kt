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

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.LocalFirewallResetRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
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
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.core.ifw.getOrThrow
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.withIfwEditorAccess
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val MAIN = declaration("Main")
private val SYNC = declaration("Sync", ComponentType.SERVICE)
private val OWNER_APP = AppKey(PKG, OWNER)

private fun outcome(component: ComponentDeclaration, pm: PmSetting, ifw: IfwBlock) = ComponentOutcome(ComponentKey(component.id, OWNER), ControlState(pm, ifw), ControlResult.ACHIEVED, emptyMap())

private fun blockRequest(vararg components: ComponentDeclaration) = ControlRequest(OWNER, components.toList(), ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER))

/** The writers of component state besides a refresh: control, firewall changes, and their ordering. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComponentWritersTest : DeviceRepositoryTestBase() {

    /** Makes the control disable [MAIN] in the system and report it; an ordinary read would agree. */
    private fun controlDisablesMain() {
        componentControl.apply = {
            flow {
                system.put(OWNER, MAIN, PmSetting.DISABLED)
                emit(ControlEvent.PackageDone(PKG, listOf(outcome(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))))
                emit(ControlEvent.Finished(achieved = 1, partial = 0, failed = 0))
            }
        }
    }

    @Test
    fun givenShellRefusalWithoutIdentity_whenBlocking_thenRepositoryReportsTheShellFailure() = runTest {
        system.install(installedApp(PKG), MAIN)
        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        val controller = ShellRefusingComponentControl.create(channel, system, store, packageLocks)
        val repository = components(backgroundScope, control = controller)

        val events = repository.control(blockRequest(MAIN), operation = operation()).toList()

        val outcome = events.filterIsInstance<ControlEvent.PackageDone>().single().outcomes.single()
        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.state.pm).isEqualTo(PmSetting.DEFAULT)
        val refusal = outcome.failures.getValue(ControlLayer.PACKAGE_MANAGER) as ControlFailure.NotPermitted
        assertThat(refusal.identity).isEqualTo(PrivilegedIdentity.SHELL)
    }

    @Test
    fun givenObservedComponents_whenControl_thenReadBackStoredBeforeTheEventIsEmitted() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN, SYNC)
        repository.refresh(OWNER_APP)
        controlDisablesMain()

        repository.components(OWNER_APP).test {
            assertThat(awaitItem().first().state.pm).isEqualTo(PmSetting.DEFAULT)
            val seenAtEvent = mutableListOf<ComponentStatus>()

            val events = mutableListOf<ControlEvent>()
            repository.control(blockRequest(MAIN), operation = operation()).collect { event ->
                events += event
                if (event is ControlEvent.PackageDone) seenAtEvent += repository.components(OWNER_APP).first()
            }

            assertThat(events.last()).isEqualTo(ControlEvent.Finished(achieved = 1, partial = 0, failed = 0))
            assertThat(seenAtEvent).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), status(SYNC, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)).inOrder()
            assertThat(awaitItem()).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), status(SYNC, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)).inOrder()
        }
        // Stored from the outcome, without reading the system again.
        assertThat(system.reads("pm")).isEqualTo(1)
    }

    @Test
    fun givenObservedComponents_whenControlChangesBothLayers_thenNextItemShowsBoth() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        componentControl.apply = {
            flow { emit(ControlEvent.PackageDone(PKG, listOf(outcome(MAIN, PmSetting.DISABLED, IfwBlock.BLOCKED)))) }
        }

        repository.components(OWNER_APP).test {
            assertThat(awaitItem()).containsExactly(status(MAIN, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))

            repository.control(ControlRequest(OWNER, listOf(MAIN), ControlGoal.BLOCK, ControlLayer.entries.toSet()), operation = operation()).toList()

            assertThat(awaitItem()).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.BLOCKED))
        }
    }

    @Test
    fun givenControlOutcomeUnknown_whenControl_thenStoredUnknownNotKeptAsBefore() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        componentControl.apply = {
            flow { emit(ControlEvent.PackageDone(PKG, listOf(ComponentOutcome(ComponentKey(MAIN.id, OWNER), ControlState.UNKNOWN, ControlResult.FAILED, mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.ChannelUnavailable))))) }
        }

        repository.control(blockRequest(MAIN), operation = operation()).toList()

        assertThat(repository.components(OWNER_APP).first()).containsExactly(status(MAIN, PmSetting.UNKNOWN, IfwBlock.UNKNOWN))
    }

    @Test
    fun givenRefreshedApp_whenRuleStoreChangesTheRules_thenCacheFollows() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN, SYNC)
        repository.refresh(OWNER_APP)
        runCurrent()

        repository.components(OWNER_APP).test {
            assertThat(awaitItem().map { it.state.ifw }).containsExactly(IfwBlock.NOT_BLOCKED, IfwBlock.NOT_BLOCKED)

            store.setBlocked(PKG, mapOf(SYNC to true))
            assertThat(awaitItem().map { it.state.ifw }).containsExactly(IfwBlock.NOT_BLOCKED, IfwBlock.BLOCKED).inOrder()

            // As the rule editor saves.
            withIfwEditorAccess { store.update(PKG) { IfwRules.empty() } }
            assertThat(awaitItem().map { it.state.ifw }).containsExactly(IfwBlock.NOT_BLOCKED, IfwBlock.NOT_BLOCKED)
        }
    }

    @Test
    fun givenCachedComponent_whenBlockingAndUnblockingThroughPm_thenTheirReadBackIsAlreadyStored() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        componentControl.apply = { request ->
            flow {
                val setting = if (request.goal == ControlGoal.BLOCK) PmSetting.DISABLED else PmSetting.DEFAULT
                system.put(OWNER, MAIN, setting)
                emit(ControlEvent.PackageDone(PKG, listOf(outcome(MAIN, setting, IfwBlock.NOT_BLOCKED))))
                emit(ControlEvent.Finished(achieved = 1, partial = 0, failed = 0))
            }
        }

        val request = blockRequest(MAIN)
        repository.control(request, operation = operation()).toList()
        assertThat(repository.components(OWNER_APP).first().single().state.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(pmBlocks.of(OWNER)).containsExactly(ComponentKey(MAIN.id, OWNER))
        repository.control(request.copy(goal = ControlGoal.UNBLOCK), operation = operation()).toList()
        assertThat(repository.components(OWNER_APP).first().single().state.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
    }

    @Test
    fun givenCachedComponent_whenFirewallRulesAreImportedAndReset_thenCacheReflectsBothWrites() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        val importedRules = IfwXmlDeserializer().deserialize(
            """<rules><activity block="true" log="false"><component-filter name="$PKG/${MAIN.id.className}" /></activity></rules>""",
        )
        val reset = LocalFirewallResetRepository({ true }, store)

        repository.components(OWNER_APP).test {
            assertThat(awaitItem().single().state.ifw).isEqualTo(IfwBlock.NOT_BLOCKED)

            store.update(PKG) { importedRules }.getOrThrow()
            assertThat(awaitItem().single().state.ifw).isEqualTo(IfwBlock.BLOCKED)

            assertThat(reset.reset(PKG) { _, _, _ -> }).isEqualTo(1)
            assertThat(awaitItem().single().state.ifw).isEqualTo(IfwBlock.NOT_BLOCKED)
        }
    }

    @Test
    fun givenRefreshHoldingAnOldRead_whenControlFinishesMeanwhile_thenControlStoredAfterAndWins() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        system.afterPmRead = {
            reading.complete(Unit)
            release.await()
        }
        controlDisablesMain()

        val refresh = async { repository.refresh(OWNER_APP) }
        reading.await()
        system.afterPmRead = {}
        val control = async { repository.control(blockRequest(MAIN), operation = operation()).toList() }
        runCurrent()

        // The control's result waits for the refresh, which read DEFAULT before the change.
        assertThat(control.isCompleted).isFalse()
        release.complete(Unit)
        refresh.await()
        control.await()
        assertThat(repository.components(OWNER_APP).first()).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenControlDone_whenRefreshFollows_thenItReadsTheChangedSystem() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        controlDisablesMain()

        repository.control(blockRequest(MAIN), operation = operation()).toList()
        repository.refresh(OWNER_APP)

        assertThat(repository.components(OWNER_APP).first()).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
    }
}
