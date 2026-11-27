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

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.history.OperationRecorder
import com.merxury.blocker.core.data.history.OperationScope
import com.merxury.blocker.core.database.history.OperationEntity
import com.merxury.blocker.core.database.history.asExternalModel
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlLayer.PACKAGE_MANAGER
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
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.backup.BlockerRuleImporter
import com.merxury.blocker.core.rule.backup.MatRuleImporter
import com.merxury.blocker.core.rule.backup.PackageManagerReset
import com.merxury.blocker.core.rule.backup.RuleRunResult
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.device.FakePmBlockRecordSeeder
import com.merxury.blocker.core.testing.storage.FakeBackupStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OperationRecordingTest : DeviceRepositoryTestBase() {
    private val history get() = historyDb.operationHistoryDao()
    private val start = Instant.parse("2026-11-27T00:00:00Z")
    private var now = start
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val recorder = OperationRecorder({ history }, clock)
    private val service = declaration("Sync", ComponentType.SERVICE)
    private val key = ComponentKey(service.id, OWNER)
    private val clear = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
    private val blocked = clear.copy(ifw = IfwBlock.BLOCKED)

    @Test
    fun givenBlockedComponent_whenControlled_thenOneOperationWithBeforeAndAfterIsStored() = runTest {
        val repository = repository()
        system.install(installedApp(PKG, WORK), service)
        store.setBlocked(PKG, mapOf(service to true))

        recorder.withOperation(OperationSource.APP_DETAIL, PKG) {
            repository.control(request(ControlGoal.UNBLOCK).copy(user = WORK), it).toList()
        }

        val operation = history.observeOperations().first().single()
        assertThat(operation.timestamp).isEqualTo(start)
        assertThat(operation.source).isEqualTo(OperationSource.APP_DETAIL)
        assertThat(operation.ref).isEqualTo(PKG)
        assertThat(operation.entryCount).isEqualTo(1)
        assertThat(entries(operation.id)).containsExactly(
            OperationEntry(operation.id, ComponentKey(service.id, WORK), ComponentType.SERVICE, blocked, clear),
        )
    }

    @Test
    fun givenUnchangedComponent_whenControlled_thenNoOperationIsStored() = runTest {
        val repository = repository()
        store.setBlocked(PKG, mapOf(service to true))

        recorder.withOperation(OperationSource.SEARCH) { repository.control(request(), it).toList() }

        assertThat(history.observeOperations().first()).isEmpty()
    }

    @Test
    fun givenTwoChunks_whenBatchRuns_thenOneOperationOwnsBothPackages() = runTest {
        val firstPackage = List(ComponentBatchRunner.BATCH_LIMIT) { declaration("Service$it", ComponentType.SERVICE) }
        val otherService = declaration("Other", ComponentType.SERVICE, OTHER_PKG)
        val target = firstPackage + otherService
        val repository = repository()
        system.install(installedApp(PKG), *firstPackage.toTypedArray())
        system.install(installedApp(OTHER_PKG), otherService)
        val runner = ComponentBatchRunner(repository, backgroundScope, controlActivity, recorder)

        runBatch(runner, BatchKey.Search(OWNER), target, ControlGoal.BLOCK)

        val operation = history.observeOperations().first().single()
        assertThat(operation.entryCount).isEqualTo(target.size)
        val recorded = entries(operation.id)
        assertThat(recorded.map { it.key.id }).containsExactlyElementsIn(target.map { it.id })
        assertThat(recorded.map { it.before }.toSet()).containsExactly(clear)
        assertThat(recorded.map { it.after }.toSet()).containsExactly(blocked)
    }

    @Test
    fun givenEveryBatchKey_whenBatchesRun_thenOperationsHaveTheirOwnSourcesAndReferences() = runTest {
        val repository = repository()
        val runner = ComponentBatchRunner(repository, backgroundScope, controlActivity, recorder)
        val app = AppKey(PKG, OWNER)
        val cases = listOf(
            BatchKey.App(app) to (OperationSource.APP_DETAIL to PKG),
            BatchKey.Search(OWNER) to (OperationSource.SEARCH to null),
            BatchKey.GeneralRules(OWNER) to (OperationSource.GENERAL_RULE to null),
            BatchKey.Rule(42, OWNER) to (OperationSource.RULE_DETAIL to "42"),
            BatchKey.Debloater(app) to (OperationSource.DEBLOATER to PKG),
        )

        cases.forEachIndexed { index, (key, _) ->
            runBatch(runner, key, listOf(service), if (index % 2 == 0) ControlGoal.BLOCK else ControlGoal.UNBLOCK)
            now += 1.days
        }

        assertThat(history.observeOperations().first().map { it.source to it.ref })
            .containsExactlyElementsIn(cases.map { it.second }.asReversed()).inOrder()
    }

    @Test
    fun givenSameComponentTwiceInOneScope_whenRecorded_thenFirstBeforeAndLastAfterAreKept() = runTest {
        val repository = repository()

        recorder.withOperation(OperationSource.EXTERNAL_PROVIDER) {
            repository.control(request(), it).toList()
            // PM changed outside Blocker between requests. The second request clears the IFW block.
            system.put(OWNER, service, PmSetting.ENABLED)
            repository.control(request(ControlGoal.UNBLOCK), it).toList()
        }

        val operation = history.observeOperations().first().single()
        assertThat(entries(operation.id)).containsExactly(
            OperationEntry(operation.id, key, service.type, clear, clear.copy(pm = PmSetting.ENABLED)),
        )
    }

    @Test
    fun givenKeepKnownState_whenReadBackIsUnknown_thenHistoryStoresTheRealReadBack() = runTest {
        val repository = repository()
        assertThat(repository.refresh(AppKey(PKG, OWNER))).isEqualTo(ReadResult.Success(Unit))
        system.afterPmRead = { app -> system.pmFailures[app.packageName] = ReadFailure.ChannelUnavailable }

        recorder.withOperation(OperationSource.EXTERNAL_PROVIDER) {
            repository.control(request(), keepKnownState = true, operation = it).toList()
        }

        assertThat(repository.component(key).first()?.state).isEqualTo(blocked)
        val operation = history.observeOperations().first().single()
        assertThat(entries(operation.id).single().before).isEqualTo(clear)
        assertThat(entries(operation.id).single().after).isEqualTo(blocked.copy(pm = PmSetting.UNKNOWN))
    }

    @Test
    fun givenHistoryWriteFails_whenControlled_thenControlResultIsUnchanged() = runTest {
        val repository = repository()
        assertThat(repository.refresh(AppKey(PKG, OWNER))).isEqualTo(ReadResult.Success(Unit))
        withContext(Dispatchers.IO) {
            historyDb.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER reject_history BEFORE INSERT ON operation
                BEGIN
                    SELECT RAISE(ABORT, 'history write failed');
                END
                """.trimIndent(),
            )
        }

        val events = recorder.withOperation(OperationSource.APP_DETAIL, PKG) {
            repository.control(request(), it).toList()
        }

        val outcome = events.filterIsInstance<ControlEvent.PackageDone>().single().outcomes.single()
        assertThat(outcome.result).isEqualTo(ControlResult.ACHIEVED)
        assertThat(outcome.state).isEqualTo(blocked)
        assertThat(events.last()).isEqualTo(ControlEvent.Finished(achieved = 1, partial = 0, failed = 0))
        assertThat(repository.component(key).first()?.state).isEqualTo(blocked)
        assertThat(history.observeOperations().first()).isEmpty()
    }

    @Test
    fun givenUnrecordedScope_whenControlled_thenNothingIsStored() = runTest {
        val repository = repository()

        val outcome = repository.control(request(), OperationScope.Unrecorded).first() as ControlEvent.PackageDone

        assertThat(outcome.outcomes.single().state).isEqualTo(blocked)
        assertThat(history.observeOperations().first()).isEmpty()
    }

    @Test
    fun givenOperationOlderThanNinetyDays_whenNewOperationStarts_thenOldOneIsDeleted() = runTest {
        val repository = repository()
        val old = OperationEntity(timestamp = start - 90.days, source = OperationSource.SEARCH)
        history.insertOperation(old, emptyList())
        history.insertOperation(old.copy(timestamp = start - 89.days), emptyList())

        recorder.withOperation(OperationSource.APP_DETAIL, PKG) {
            now += 1.days
            repository.control(request(), it).toList()
        }

        assertThat(history.observeOperations().first().map { it.timestamp })
            .containsExactly(start, start - 89.days).inOrder()
    }

    @Test
    fun givenOwnComponents_whenBlockRefused_thenNoEntry() = runTest {
        val own = declaration("Own", ComponentType.SERVICE, SELF_PKG)
        val repository = repository()
        system.install(installedApp(SELF_PKG), own)

        val events = recorder.withOperation(OperationSource.APP_DETAIL, SELF_PKG) {
            repository.control(request().copy(components = listOf(own)), it).toList()
        }

        val outcome = events.filterIsInstance<ControlEvent.PackageDone>().single().outcomes.single()
        assertThat(outcome.failures.values).containsExactly(ControlFailure.ProtectedComponent)
        assertThat(history.observeOperations().first()).isEmpty()
    }

    @Test
    fun givenMatRulesForTwoPackages_whenImported_thenOneOperationOwnsBothPackages() = runTest {
        val repository = repository()
        val other = declaration("Other", ComponentType.SERVICE, OTHER_PKG)
        system.install(installedApp(OTHER_PKG), other)
        val userData = TestUserDataRepository().apply { setControllerType(ControllerType.IFW) }
        val importer = MatRuleImporter(apps, repository, channel, userData, recorder)

        val result = importer.import(listOf(service.id.flatten(), other.id.flatten()), OWNER)

        assertThat(result).isInstanceOf(RuleRunResult.Completed::class.java)
        assertOperation(OperationSource.IMPORT_MAT_RULE, service, other)
    }

    @Test
    fun givenBackupForTwoPackages_whenImported_thenOneOperationOwnsEveryRestoreRequest() = runTest {
        val repository = repository()
        val other = declaration("Other", ComponentType.SERVICE, OTHER_PKG)
        val enabledByImport = declaration("Enabled", ComponentType.SERVICE)
        system.install(installedApp(PKG), service, enabledByImport)
        system.install(installedApp(OTHER_PKG), other)
        store.setBlocked(PKG, mapOf(enabledByImport to true))
        val storage = FakeBackupStorage()
        val set = BackupSet.OWNER
        val json = Json
        for ((packageName, target) in listOf(PKG to listOf(service, enabledByImport), OTHER_PKG to listOf(other))) {
            val entries = target.map {
                ComponentRule(packageName, it.id.className, it == enabledByImport, it.type, ControllerType.IFW)
            }.toMutableList()
            storage.putFile(set.fileOf(packageName), json.encodeToString(BlockerRule(packageName, components = entries)))
        }
        val importer = BlockerRuleImporter(apps, repository, channel, json, recorder)

        val result = importer.import(storage, set, OWNER)

        assertThat(result).isInstanceOf(RuleRunResult.Completed::class.java)
        assertOperation(OperationSource.IMPORT_BLOCKER_RULE, service, enabledByImport, other)
    }

    @Test
    fun givenPmBlocksAcrossTwoPackages_whenReset_thenOneOperationOwnsBothPackages() = runTest {
        val repository = components(backgroundScope)
        val other = declaration("Other", ComponentType.SERVICE, OTHER_PKG)
        val target = listOf(service, other)
        for (component in target) {
            system.install(installedApp(component.id.packageName), component)
            system.put(OWNER, component, PmSetting.DISABLED)
        }
        pmBlocks.recordNew(target.map { ComponentKey(it.id, OWNER) })
        componentControl.apply = { request ->
            flow {
                assertThat(request.layers).containsExactly(PACKAGE_MANAGER)
                val component = request.components.single()
                system.put(request.user, component, PmSetting.DEFAULT)
                val outcome = ComponentOutcome(ComponentKey(component.id, request.user), clear, ControlResult.ACHIEVED, emptyMap())
                emit(ControlEvent.PackageDone(component.id.packageName, listOf(outcome), pmWrites = setOf(component.id)))
                emit(ControlEvent.Finished(achieved = 1, partial = 0, failed = 0))
            }
        }
        val reset = PackageManagerReset(repository, channel, FakePmBlockRecordSeeder(), recorder)

        val result = reset.reset(OWNER)

        assertThat(result).isInstanceOf(RuleRunResult.Completed::class.java)
        assertOperation(OperationSource.RESET_PM, service, other)
    }

    private suspend fun assertOperation(source: OperationSource, vararg components: ComponentDeclaration) {
        val operation = history.observeOperations().first().single()
        assertThat(operation.source).isEqualTo(source)
        assertThat(operation.ref).isNull()
        assertThat(entries(operation.id).map { it.key.id }).containsExactlyElementsIn(components.map { it.id })
    }

    private fun TestScope.repository(): ComponentStatusRepository {
        system.install(installedApp(PKG), service)
        return components(backgroundScope, ShellRefusingComponentControl.create(channel, system, store, packageLocks))
    }

    private fun request(goal: ControlGoal = ControlGoal.BLOCK) = ControlRequest(OWNER, listOf(service), goal, setOf(INTENT_FIREWALL))

    private suspend fun entries(id: Long) = history.getEntries(id).map { it.asExternalModel() }

    private suspend fun runBatch(
        runner: ComponentBatchRunner,
        key: BatchKey,
        target: List<ComponentDeclaration>,
        goal: ControlGoal,
    ) {
        val run = checkNotNull(runner.reserve(key, goal, target.map { it.id }))
        runner.start(run, target, setOf(INTENT_FIREWALL))
        assertThat(run.outcomes.await()).hasSize(target.size)
    }
}
