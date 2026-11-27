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

package com.merxury.blocker.core.data.history

import com.merxury.blocker.core.database.history.OperationEntity
import com.merxury.blocker.core.database.history.OperationHistoryDao
import com.merxury.blocker.core.database.history.asEntity
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Groups the component changes made by one action. */
@Singleton
class OperationRecorder @Inject constructor(
    private val history: Lazy<OperationHistoryDao>,
    private val clock: Clock,
) {
    suspend fun <T> withOperation(
        source: OperationSource,
        ref: String? = null,
        block: suspend (OperationScope) -> T,
    ): T = block(OperationScope.Recording(history, clock, OperationEntity(timestamp = clock.now(), source = source, ref = ref)))
}

/** The operation shared by every component request for an action. */
sealed class OperationScope {
    /** Used for changes managed and tracked by wake protection. */
    data object Unrecorded : OperationScope() {
        override suspend fun append(packageName: String, user: UserId, entries: List<OperationEntry>) = Unit
    }

    internal abstract suspend fun append(packageName: String, user: UserId, entries: List<OperationEntry>)

    internal class Recording(
        private val history: Lazy<OperationHistoryDao>,
        private val clock: Clock,
        private val header: OperationEntity,
    ) : OperationScope() {
        private val lock = Mutex()
        private var id: Long? = null

        // History restores run sequentially. The observer reports only components whose IFW rules changed.
        private val ifwWrites = mutableSetOf<ComponentKey>()

        /** Creates the header even when undo found all its targets already restored. */
        internal suspend fun ensureHeader(): Long = lock.withLock { id ?: insertOperation(emptyList()) }

        internal fun undoIfwWriteObserver(user: UserId): ComponentWriteObserver? {
            if (header.source != OperationSource.UNDO) return null
            return object : ComponentWriteObserver {
                override fun canStart() = true

                override fun onWrite(components: Collection<ComponentId>) {
                    components.mapTo(ifwWrites) { ComponentKey(it, user) }
                }
            }
        }

        internal fun wroteIfw(key: ComponentKey): Boolean = key in ifwWrites

        override suspend fun append(packageName: String, user: UserId, entries: List<OperationEntry>) {
            if (entries.isEmpty()) return
            try {
                lock.withLock {
                    val operationId = id
                    if (operationId == null) {
                        insertOperation(entries)
                    } else {
                        history.get().insertEntries(entries.map { it.copy(operationId = operationId).asEntity() })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot record %s component changes for package %s, user %d", header.source, packageName, user.value)
            }
        }

        private suspend fun insertOperation(entries: List<OperationEntry>): Long {
            val dao = history.get()
            dao.deleteOlderThan(clock.now() - 90.days)
            return dao.insertOperation(header, entries.map { it.asEntity() }).also { id = it }
        }
    }
}

internal suspend fun OperationScope.recordChanges(
    event: ControlEvent.PackageDone,
    user: UserId,
    before: Map<ComponentId, ControlState>,
    types: Map<ComponentId, ComponentType>,
) {
    if (this === OperationScope.Unrecorded) return
    val entries = event.outcomes.mapNotNull { outcome ->
        val id = outcome.key.id
        val previous = before[id] ?: return@mapNotNull null
        if (previous == outcome.state) return@mapNotNull null
        OperationEntry(
            operationId = 0,
            key = outcome.key,
            type = types.getValue(id),
            before = previous,
            after = outcome.state,
        )
    }
    append(event.packageName, user, entries)
}

@Module
@InstallIn(SingletonComponent::class)
internal object OperationClockModule {
    @Provides
    fun provideClock(): Clock = Clock.System
}
