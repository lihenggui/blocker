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
import com.merxury.blocker.core.control.ComponentControl
import com.merxury.blocker.core.data.history.LocalOperationHistoryRepository
import com.merxury.blocker.core.data.history.OperationRecorder
import com.merxury.blocker.core.data.history.OperationScope
import com.merxury.blocker.core.database.history.OperationEntity
import com.merxury.blocker.core.database.history.asEntity
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlLayer.PACKAGE_MANAGER
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.PmSetting.DEFAULT
import com.merxury.blocker.core.model.component.PmSetting.DISABLED
import com.merxury.blocker.core.model.component.PmSetting.ENABLED
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.model.system.ReadFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OperationHistoryUndoTest : DeviceRepositoryTestBase() {
    private val history get() = historyDb.operationHistoryDao()
    private val start = Instant.parse("2026-11-27T00:00:00Z")
    private val recorder = OperationRecorder(
        { history },
        object : Clock {
            override fun now() = start + 10.seconds
        },
    )
    private val service = declaration("Sync", ComponentType.SERVICE)
    private val other = declaration("Other", ComponentType.SERVICE, OTHER_PKG)
    private val clear = ControlState(DEFAULT, IfwBlock.NOT_BLOCKED)
    private val requests = mutableListOf<ControlRequest>()
    private val empty = UndoResult(0, 0, 0, 0)

    @Test
    fun givenBlockedOperation_whenUndone_thenComponentIsUnblockedAndOperationMarkedUndone() = runTest {
        val repository = repository()
        system.put(OWNER, service, DISABLED)
        pmBlocks.record(listOf(service.key()))
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(restored = 1))

        val undo = repository.observeOperations().first().first()
        assertThat(pm(service)).isEqualTo(DEFAULT)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
        assertThat(undo.source).isEqualTo(OperationSource.UNDO)
        assertThat(undo.ref).isEqualTo(id.toString())
        assertThat(repository.getEntries(undo.id)).containsExactly(entry(DISABLED, DEFAULT).copy(operationId = undo.id))
        assertThat(undoneBy(repository, id)).isEqualTo(undo.id)
        assertThat(repository.undoOperation(id)).isEqualTo(empty)
        assertThat(requests).containsExactly(ControlRequest(OWNER, listOf(service), ControlGoal.UNBLOCK, setOf(PACKAGE_MANAGER), DISABLED))
    }

    @Test
    fun givenUnblockedOperation_whenUndone_thenComponentIsBlockedAgainAndPmRecordJournaled() = runTest {
        val repository = repository()
        val id = record(entry(DISABLED, DEFAULT))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(restored = 1))

        assertThat(pm(service)).isEqualTo(DISABLED)
        assertThat(pmBlocks.of(OWNER)).containsExactly(service.key())
        assertThat(requests.single().expectedPm).isEqualTo(DEFAULT)
    }

    @Test
    fun givenExplicitEnable_whenUndoneToEnabled_thenEnableExplicitIsSent() = runTest {
        val repository = repository()
        system.put(OWNER, service, DISABLED)
        val id = record(entry(ENABLED, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(restored = 1))

        assertThat(requests.single().goal).isEqualTo(ControlGoal.ENABLE_EXPLICIT)
        assertThat(pm(service)).isEqualTo(ENABLED)
    }

    @Test
    fun givenComponentChangedSince_whenUndone_thenLayerIsSkippedAndOperationStaysEligible() = runTest {
        val repository = repository()
        system.put(OWNER, service, ENABLED)
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(skipped = 1))

        assertThat(requests).isEmpty()
        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenComponentAlreadyAtTarget_whenUndone_thenCountedAlreadyAtTargetWithoutWrite() = runTest {
        val repository = repository()
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(alreadyAtTarget = 1))

        val undo = repository.observeOperations().first().first()
        assertThat(requests).isEmpty()
        assertThat(undo.source).isEqualTo(OperationSource.UNDO)
        assertThat(undo.entryCount).isEqualTo(0)
        assertThat(undoneBy(repository, id)).isEqualTo(undo.id)
    }

    @Test
    fun givenEnabledToDefaultTarget_whenUndone_thenSkipped() = runTest {
        val repository = repository()
        system.put(OWNER, service, ENABLED)
        val id = record(entry(DEFAULT, ENABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(skipped = 1))

        assertThat(requests).isEmpty()
        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenUnknownState_whenUndone_thenSkipped() = runTest {
        val repository = repository()
        val currentUnknown = record(entry(DEFAULT, DISABLED))
        val targetUnknown = record(entry(PmSetting.UNKNOWN, DISABLED))
        system.pmFailures[PKG] = ReadFailure.ChannelUnavailable

        assertThat(repository.undoOperation(currentUnknown)).isEqualTo(empty.copy(skipped = 1))
        system.pmFailures.clear()
        system.put(OWNER, service, DISABLED)
        assertThat(repository.undoOperation(targetUnknown)).isEqualTo(empty.copy(skipped = 1))

        assertThat(requests).isEmpty()
    }

    @Test
    fun givenRemovedComponent_whenUndone_thenSkipped() = runTest {
        val status = components(backgroundScope)
        val repository = LocalOperationHistoryRepository(history, status, recorder)
        val app = AppKey(PKG, OWNER)
        system.install(installedApp(PKG), service)
        status.refresh(app)
        system.install(installedApp(PKG, lastUpdateTime = 3_000), declaration("Different"))
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(skipped = 1))
        system.apps.remove(app)
        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(skipped = 1))

        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenWakeOwnedPmBlock_whenUndone_thenPmLayerSkipped() = runTest {
        val repository = repository(claimedWakeBlocks = { user, candidates ->
            assertThat(user).isEqualTo(OWNER)
            candidates.filter { it == service.id }
        })
        system.put(OWNER, service, DISABLED)
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(skipped = 1))

        assertThat(pm(service)).isEqualTo(DISABLED)
        assertThat(requests).isEmpty()
        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenIfwOnlyReceiverChange_whenUndone_thenPmIsUntouched() = runTest {
        val receiver = declaration("Boot", ComponentType.RECEIVER)
        val realControl = ShellRefusingComponentControl.create(channel, system, store, packageLocks)
        val repository = repository(control = realControl)
        system.install(installedApp(PKG), receiver)
        system.put(OWNER, receiver, DISABLED)
        store.setBlocked(PKG, mapOf(receiver to true))
        val before = ControlState(DISABLED, IfwBlock.NOT_BLOCKED)
        val id = record(OperationEntry(0, receiver.key(), receiver.type, before, before.copy(ifw = IfwBlock.BLOCKED)))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(restored = 1))

        assertThat(store.blocks(PKG, listOf(receiver))[receiver.id]).isEqualTo(IfwBlock.NOT_BLOCKED)
        assertThat(pm(receiver)).isEqualTo(DISABLED)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
        assertThat(undoneBy(repository, id)).isNotNull()
    }

    @Test
    fun givenIfwTargetConditional_whenUndone_thenPmCanRestoreAndComponentIsSkipped() = runTest {
        val repository = repository()
        system.put(OWNER, service, DISABLED)
        val before = clear.copy(ifw = IfwBlock.CONDITIONAL)
        val after = clear.copy(pm = DISABLED)
        val id = record(OperationEntry(0, service.key(), service.type, before, after))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(skipped = 1))

        assertThat(pm(service)).isEqualTo(DEFAULT)
        assertThat(requests.single().layers).containsExactly(PACKAGE_MANAGER)
        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenTwoOperationsOnSameComponent_whenUndoneSince_thenEarliestBeforeIsRestored() = runTest {
        val repository = repository()
        // IDs intentionally disagree with timestamps so the fold has to use chronological order.
        val later = record(entry(DISABLED, DEFAULT), timestamp = start + 1.seconds)
        val earlier = record(entry(ENABLED, DISABLED))

        assertThat(repository.undoSince(start)).isEqualTo(empty.copy(restored = 1))

        assertThat(pm(service)).isEqualTo(ENABLED)
        assertThat(system.reads("declarations ")).isEqualTo(1)
        val undo = repository.observeOperations().first().first()
        assertThat(undo.ref).isNull()
        assertThat(undoneBy(repository, earlier)).isEqualTo(undo.id)
        assertThat(undoneBy(repository, later)).isEqualTo(undo.id)
    }

    @Test
    fun givenTwoUsers_whenUndoneSince_thenEachUserRestoredSeparately() = runTest {
        val repository = repository()
        system.install(installedApp(PKG, WORK), service)
        system.put(OWNER, service, DISABLED)
        system.put(WORK, service, DISABLED)
        record(entry(DEFAULT, DISABLED), entry(ENABLED, DISABLED, user = WORK))

        assertThat(repository.undoSince(start)).isEqualTo(empty.copy(restored = 2))

        assertThat(pm(service)).isEqualTo(DEFAULT)
        assertThat(pm(service, WORK)).isEqualTo(ENABLED)
        assertThat(requests.map { it.user }).containsExactly(OWNER, WORK)
        assertThat(system.reads("declarations ")).isEqualTo(2)
    }

    @Test
    fun givenDifferentIfwTargetsAcrossUsers_whenUndoneSince_thenOnlyFinalTargetIsMarkedUndone() = runTest {
        val repository = repository(control = ShellRefusingComponentControl.create(channel, system, store, packageLocks))
        system.install(installedApp(PKG, WORK), service)
        val blocked = clear.copy(ifw = IfwBlock.BLOCKED)
        val owner = record(OperationEntry(0, service.key(), service.type, clear, blocked))
        val work = record(OperationEntry(0, service.key(WORK), service.type, blocked, clear), timestamp = start + 1.seconds)

        assertThat(repository.undoSince(start)).isEqualTo(empty.copy(restored = 1, skipped = 1))

        assertThat(store.blocks(PKG, listOf(service))[service.id]).isEqualTo(IfwBlock.BLOCKED)
        assertThat(undoneBy(repository, owner)).isNull()
        assertThat(undoneBy(repository, work)).isNotNull()
    }

    @Test
    fun givenWriteFails_whenUndone_thenFailedAndOperationStaysEligible() = runTest {
        val repository = repository()
        system.put(OWNER, service, DISABLED)
        pmControl(failure = ControlFailure.ChannelUnavailable)
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(failed = 1))

        assertThat(pm(service)).isEqualTo(DISABLED)
        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenAchievedReadBackDiffersFromExactTarget_whenUndone_thenFailed() = runTest {
        val repository = repository()
        system.put(OWNER, service, DISABLED)
        pmControl(readBack = ENABLED)
        val id = record(entry(DEFAULT, DISABLED))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(failed = 1))

        assertThat(undoneBy(repository, id)).isNull()
    }

    @Test
    fun givenUndoOperation_whenUndoRequestedForIt_thenNothingHappens() = runTest {
        val repository = repository()
        val undo = record(entry(DEFAULT, DISABLED), source = OperationSource.UNDO)

        assertThat(repository.undoOperation(undo)).isEqualTo(empty)
        assertThat(repository.undoOperation(undo + 1)).isEqualTo(empty)
        assertThat(repository.undoSince(start)).isEqualTo(empty)

        assertThat(requests).isEmpty()
        assertThat(repository.observeOperations().first().map { it.id }).containsExactly(undo)
    }

    @Test
    fun givenUndo_whenFinished_thenOneUndoOperationIsRecorded() = runTest {
        val repository = repository()
        val second = declaration("Second", ComponentType.SERVICE)
        system.install(installedApp(PKG), service, second)
        system.install(installedApp(OTHER_PKG), other)
        system.put(OWNER, service, DISABLED)
        system.put(OWNER, second, DISABLED)
        val id = record(entry(DEFAULT, DISABLED), entry(DEFAULT, DISABLED, second), entry(DISABLED, DEFAULT, other))

        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(restored = 3))

        assertThat(requests).hasSize(2)
        assertThat(requests.first().components).containsExactly(service, second)
        assertThat(system.reads("declarations ")).isEqualTo(2)
        val undo = repository.observeOperations().first().filter { it.source == OperationSource.UNDO }.single()
        assertThat(undo.ref).isEqualTo(id.toString())
        assertThat(undo.entryCount).isEqualTo(3)
        assertThat(undoneBy(repository, id)).isEqualTo(undo.id)
    }

    @Test
    fun givenZeroEntryOperation_whenObserved_thenHidden() = runTest {
        val repository = repository()
        record()
        val visible = record(entry(DEFAULT, DISABLED))
        val undo = record(source = OperationSource.UNDO)

        repository.observeOperations().test {
            assertThat(awaitItem().map { it.id }).containsExactly(undo, visible).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenRangePartiallyRestored_whenRetried_thenCompletedLayerIsAlreadyAtTarget() = runTest {
        val repository = repository()
        system.install(installedApp(OTHER_PKG), other)
        system.put(OWNER, service, DISABLED)
        system.put(OWNER, other, ENABLED)
        val id = record(entry(DEFAULT, DISABLED), entry(DEFAULT, DISABLED, other))

        assertThat(repository.undoSince(start)).isEqualTo(empty.copy(restored = 1, skipped = 1))
        assertThat(undoneBy(repository, id)).isNull()
        system.put(OWNER, other, DISABLED)

        assertThat(repository.undoSince(start)).isEqualTo(empty.copy(restored = 1, alreadyAtTarget = 1))
        assertThat(undoneBy(repository, id)).isNotNull()
    }

    @Test
    fun givenConcurrentUndoCalls_whenFirstCompletes_thenSecondFindsNoEligibleOperation() = runTest {
        val repository = repository()
        system.put(OWNER, service, DISABLED)
        val id = record(entry(DEFAULT, DISABLED))
        val writing = CompletableDeferred<Unit>()
        val resumeWrite = CompletableDeferred<Unit>()
        pmControl(beforePackage = {
            writing.complete(Unit)
            resumeWrite.await()
        })

        val first = async { repository.undoOperation(id) }
        writing.await()
        val second = async { repository.undoSince(start) }
        resumeWrite.complete(Unit)

        assertThat(first.await()).isEqualTo(empty.copy(restored = 1))
        assertThat(second.await()).isEqualTo(empty)
        assertThat(requests).hasSize(1)
    }

    @Test
    fun givenUndoCancelledBetweenPackages_whenRetried_thenPreviouslyRestoredPackageStaysDone() = runTest {
        val repository = repository()
        system.install(installedApp(OTHER_PKG), other)
        system.put(OWNER, service, DISABLED)
        system.put(OWNER, other, DISABLED)
        val id = record(entry(DEFAULT, DISABLED), entry(DEFAULT, DISABLED, other))
        val waiting = CompletableDeferred<Unit>()
        val resumeWrite = CompletableDeferred<Unit>()
        pmControl(beforePackage = { name ->
            if (name == OTHER_PKG) {
                waiting.complete(Unit)
                resumeWrite.await()
            }
        })

        val undo = launch { repository.undoOperation(id) }
        waiting.await()
        undo.cancelAndJoin()

        assertThat(pm(service)).isEqualTo(DEFAULT)
        assertThat(pm(other)).isEqualTo(DISABLED)
        assertThat(undoneBy(repository, id)).isNull()
        assertThat(repository.observeOperations().first().first().entryCount).isEqualTo(1)
        pmControl()
        assertThat(repository.undoOperation(id)).isEqualTo(empty.copy(restored = 1, alreadyAtTarget = 1))
    }

    @Test
    fun givenExpectedPmFromHistoryUndo_whenControlled_thenPmRecordsReconciled() = runTest {
        val status = components(backgroundScope)
        val request = ControlRequest(OWNER, listOf(service), ControlGoal.UNBLOCK, setOf(PACKAGE_MANAGER), DISABLED)
        system.put(OWNER, service, DISABLED)
        pmBlocks.record(listOf(service.key()))
        pmControl()

        recorder.withOperation(OperationSource.UNDO) { status.restore(request, it).toList() }

        assertThat(pm(service)).isEqualTo(DEFAULT)
        assertThat(pmBlocks.of(OWNER)).isEmpty()
    }

    @Test
    fun givenExpectedPmFromUnrecordedWakeUndo_whenControlled_thenPmRecordsStayForWakeToReconcile() = runTest {
        val status = components(backgroundScope)
        val request = ControlRequest(OWNER, listOf(service), ControlGoal.UNBLOCK, setOf(PACKAGE_MANAGER), DISABLED)
        system.put(OWNER, service, DISABLED)
        pmBlocks.record(listOf(service.key()))
        pmControl()

        status.control(request, OperationScope.Unrecorded).toList()

        assertThat(pm(service)).isEqualTo(DEFAULT)
        assertThat(pmBlocks.of(OWNER)).containsExactly(service.key())
    }

    private fun TestScope.repository(
        control: ComponentControl = componentControl,
        claimedWakeBlocks: suspend (UserId, Set<ComponentId>) -> List<ComponentId> = { _, _ -> emptyList() },
    ): LocalOperationHistoryRepository {
        system.install(installedApp(PKG), service)
        pmControl()
        return LocalOperationHistoryRepository(history, components(backgroundScope, control, claimedWakeBlocks), recorder)
    }

    private fun pmControl(
        failure: ControlFailure? = null,
        readBack: PmSetting? = null,
        beforePackage: suspend (String) -> Unit = {},
    ) {
        componentControl.apply = { request ->
            flow {
                requests += request
                assertThat(request.layers).containsExactly(PACKAGE_MANAGER)
                val next = when (request.goal) {
                    ControlGoal.BLOCK -> DISABLED
                    ControlGoal.UNBLOCK -> DEFAULT
                    ControlGoal.ENABLE_EXPLICIT -> ENABLED
                    ControlGoal.ENABLE -> error("Expected an exact PM goal")
                }
                for ((pkg, declarations) in request.components.groupBy { it.id.packageName }) {
                    beforePackage(pkg)
                    val outcomes = declarations.map { declaration ->
                        val before = pm(declaration, request.user)
                        assertThat(before).isEqualTo(request.expectedPm)
                        if (failure == null) system.put(request.user, declaration, readBack ?: next)
                        ComponentOutcome(
                            declaration.key(request.user),
                            clear.copy(pm = pm(declaration, request.user)),
                            if (failure == null) ControlResult.ACHIEVED else ControlResult.FAILED,
                            if (failure == null) emptyMap() else mapOf(PACKAGE_MANAGER to failure),
                        )
                    }
                    val written = outcomes.filter { it.state.pm == next && failure == null }.mapTo(mutableSetOf()) { it.key.id }
                    emit(ControlEvent.PackageDone(pkg, outcomes, pmWrites = written))
                }
            }
        }
    }

    private fun ComponentDeclaration.key(user: UserId = OWNER) = ComponentKey(id, user)

    private fun pm(declaration: ComponentDeclaration, user: UserId = OWNER) = system.settings[user to declaration.id] ?: DEFAULT

    private fun entry(before: PmSetting, after: PmSetting, component: ComponentDeclaration = service, user: UserId = OWNER) = OperationEntry(0, component.key(user), component.type, clear.copy(pm = before), clear.copy(pm = after))

    private suspend fun record(
        vararg entries: OperationEntry,
        source: OperationSource = OperationSource.APP_DETAIL,
        timestamp: Instant = start,
    ) = history.insertOperation(OperationEntity(timestamp = timestamp, source = source), entries.map { it.asEntity() })

    private suspend fun undoneBy(repository: LocalOperationHistoryRepository, id: Long) = repository.observeOperations().first().single { it.id == id }.undoneByOperationId
}
