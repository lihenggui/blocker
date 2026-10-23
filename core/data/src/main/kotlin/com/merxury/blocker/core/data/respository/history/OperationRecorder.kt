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

package com.merxury.blocker.core.data.respository.history

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

interface OperationScope {
    suspend fun <R> track(components: List<ComponentInfo>, action: suspend () -> R): R
}

/** Records per-component snapshots in a scope shared by nested calls. */
@Singleton
class OperationRecorder @Inject constructor(
    private val historyRepository: OperationHistoryRepository,
    @RootApiControl private val pmController: IController,
    @IfwControl private val ifwController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun <T> withOperation(
        source: OperationSource,
        ref: String? = null,
        block: suspend OperationScope.() -> T,
    ): T {
        // A provider call encloses several repository calls in one operation.
        currentCoroutineContext()[RecordingScope]?.let { return block(it) }
        val timestamp = Clock.System.now()
        historyRepository.deleteOlderThan(timestamp - 90.days)
        val scope = RecordingScope(this, source, ref, timestamp)
        return withContext(ioDispatcher + scope) { block(scope) }
    }

    /** Also used by undo to obtain the id of its row, including an undo with no changes. */
    suspend fun logOnly(source: OperationSource, ref: String? = null): Long {
        val scope = currentCoroutineContext()[RecordingScope]
        if (scope != null && scope.source == source && scope.ref == ref) {
            return scope.ensureOperation()
        }
        return withOperation(source, ref) {
            checkNotNull(currentCoroutineContext()[RecordingScope]).ensureOperation()
        }
    }

    /** Shares the repository's real after-state read with the active tracker. */
    internal suspend fun readComponentState(component: ComponentInfo): ComponentInfo {
        val state = readState(component)
        currentCoroutineContext()[RecordingScope]?.afterStates?.set(component.packageName to component.name, state)
        return state
    }

    private suspend fun readState(component: ComponentInfo) = component.copy(
        pmBlocked = !pmController.checkComponentEnableState(component.packageName, component.name),
        ifwBlocked = !ifwController.checkComponentEnableState(component.packageName, component.name),
    )

    private class RecordingScope(
        private val recorder: OperationRecorder,
        val source: OperationSource,
        val ref: String?,
        private val timestamp: Instant,
    ) : AbstractCoroutineContextElement(Key),
        OperationScope {
        companion object Key : CoroutineContext.Key<RecordingScope>

        private var operationId: Long? = null
        val afterStates = mutableMapOf<Pair<String, String>, ComponentInfo>()

        suspend fun ensureOperation(): Long = operationId ?: recorder.historyRepository.insertOperation(
            Operation(id = 0, timestamp = timestamp, source = source, ref = ref),
            emptyList(),
        ).also { operationId = it }

        override suspend fun <R> track(components: List<ComponentInfo>, action: suspend () -> R): R {
            val before = components.distinctBy { it.packageName to it.name }
                .map { recorder.readState(it) }
            afterStates.clear()
            try {
                return action()
            } finally {
                // Capture partial changes even when a worker is cancelled or a controller throws.
                withContext(NonCancellable) {
                    val entries = before.mapNotNull { component ->
                        val after = try {
                            afterStates[component.packageName to component.name] ?: recorder.readState(component)
                        } catch (e: Exception) {
                            Timber.e(e, "Cannot read after-state for ${component.packageName}/${component.name}")
                            return@mapNotNull null
                        }
                        if (component.pmBlocked == after.pmBlocked && component.ifwBlocked == after.ifwBlocked) {
                            return@mapNotNull null
                        }
                        OperationEntry(
                            operationId = operationId ?: 0,
                            packageName = component.packageName,
                            componentName = component.name,
                            componentType = component.type,
                            beforePmBlocked = component.pmBlocked,
                            beforeIfwBlocked = component.ifwBlocked,
                            afterPmBlocked = after.pmBlocked,
                            afterIfwBlocked = after.ifwBlocked,
                        )
                    }
                    if (entries.isNotEmpty()) {
                        val id = operationId
                        if (id == null) {
                            operationId = recorder.historyRepository.insertOperation(
                                Operation(id = 0, timestamp = timestamp, source = source, ref = ref),
                                entries,
                            )
                        } else {
                            recorder.historyRepository.insertEntries(entries.map { it.copy(operationId = id) })
                        }
                    }
                    afterStates.clear()
                }
            }
        }
    }
}
