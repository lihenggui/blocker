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

package com.merxury.blocker.core.domain.history

import android.content.pm.PackageInfo
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.ShizukuControl
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.history.OperationHistoryRepository
import com.merxury.blocker.core.data.respository.history.OperationRecorder
import com.merxury.blocker.core.data.respository.history.OperationScope
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.domain.history.UndoFailureReason.COMPONENT_MISSING
import com.merxury.blocker.core.domain.history.UndoFailureReason.ERROR
import com.merxury.blocker.core.domain.history.UndoFailureReason.IFW_FAILED
import com.merxury.blocker.core.domain.history.UndoFailureReason.IFW_PERMISSION_UNAVAILABLE
import com.merxury.blocker.core.domain.history.UndoFailureReason.PM_FAILED
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import com.merxury.blocker.core.utils.PackageInfoDataSource
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Instant

enum class UndoFailureReason {
    COMPONENT_MISSING,
    PM_FAILED,
    IFW_PERMISSION_UNAVAILABLE,
    IFW_FAILED,
    ERROR,
}

/** Counts components, including a component with a failure in either dimension only once. */
data class UndoResult(
    val restored: Int,
    val skipped: Int,
    val failed: Int,
    val failureReasons: Map<ComponentInfo, List<UndoFailureReason>>,
)

class UndoOperationsUseCase @Inject constructor(
    private val historyRepository: OperationHistoryRepository,
    private val recorder: OperationRecorder,
    private val userDataRepository: UserDataRepository,
    private val componentRepository: ComponentRepository,
    private val packageInfoDataSource: PackageInfoDataSource,
    private val rootChecker: RootAvailabilityChecker,
    @RootApiControl private val pmController: IController,
    @ShizukuControl private val shizukuController: IController,
    @IfwControl private val ifwController: IController,
) {
    suspend operator fun invoke(operationId: Long): UndoResult = recorder.withOperation(UNDO) {
        val undoOperationId = recorder.logOnly(UNDO)
        val operation = historyRepository.observeOperations().first().find { it.id == operationId }
        val entries = if (operation != null && operation.undoneByOperationId == null && operation.source != RESET_IFW && operation.source != UNDO) {
            historyRepository.getEntries(operationId)
        } else {
            emptyList()
        }
        restore(entries, undoOperationId)
    }

    suspend operator fun invoke(since: Instant): UndoResult = recorder.withOperation(UNDO) {
        val undoOperationId = recorder.logOnly(UNDO)
        restore(historyRepository.getEntriesSince(since), undoOperationId)
    }

    private suspend fun OperationScope.restore(entries: List<OperationEntry>, undoOperationId: Long): UndoResult {
        val grouped = entries.groupBy { it.packageName to it.componentName }
        val pm = if (userDataRepository.userData.first().controllerType == SHIZUKU) shizukuController else pmController
        val pmInitialization = runCatching { pm.init() }.onFailure {
            if (it is CancellationException) throw it
            Timber.e(it, "Cannot initialize PM controller for undo")
        }
        val rootAvailability = runCatching { rootChecker.isRootAvailable() }.onFailure {
            if (it is CancellationException) throw it
            Timber.e(it, "Cannot check IFW permission for undo")
        }
        val packages = mutableMapOf<String, Result<PackageInfo>>()
        val failures = mutableMapOf<Pair<String, String>, List<UndoFailureReason>>()
        var restored = 0
        var skipped = 0
        grouped.forEach { (key, changes) ->
            // getEntriesSince is ordered by timestamp and id, ascending.
            val target = changes.first()
            val component = ComponentInfo(target.packageName, target.componentName, target.componentType)
            val reasons = mutableListOf<UndoFailureReason>()
            var changed = false
            try {
                val info = packages.getOrPut(component.packageName) {
                    runCatching { packageInfoDataSource.getApplicationComponents(component.packageName) }
                }.getOrThrow()
                val installed = when (component.type) {
                    ACTIVITY -> info.activities?.any { it.name == component.name }
                    RECEIVER -> info.receivers?.any { it.name == component.name }
                    SERVICE -> info.services?.any { it.name == component.name }
                    PROVIDER -> info.providers?.any { it.name == component.name }
                } == true
                if (!installed) {
                    reasons.add(COMPONENT_MISSING)
                } else {
                    track(listOf(component)) {
                        // A failure in one dimension must not prevent restoring the other.
                        try {
                            val blocked = !pm.checkComponentEnableState(component.packageName, component.name)
                            if (blocked != target.beforePmBlocked) {
                                pmInitialization.getOrThrow()
                                val succeeded = if (target.beforePmBlocked) pm.disable(component) else pm.enable(component)
                                val after = !pm.checkComponentEnableState(component.packageName, component.name)
                                if (!succeeded || after != target.beforePmBlocked) {
                                    reasons.add(PM_FAILED)
                                } else {
                                    changed = true
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.e(e, "Cannot restore PM state for ${component.packageName}/${component.name}")
                            reasons.add(PM_FAILED)
                        }
                        try {
                            val needsIfw = target.beforeIfwBlocked || changes.any { it.beforeIfwBlocked != it.afterIfwBlocked }
                            val hasRoot = rootAvailability.getOrThrow()
                            if (needsIfw && !hasRoot) {
                                reasons.add(IFW_PERMISSION_UNAVAILABLE)
                            } else if (hasRoot) {
                                val blocked = !ifwController.checkComponentEnableState(component.packageName, component.name)
                                if (blocked != target.beforeIfwBlocked) {
                                    val succeeded = if (target.beforeIfwBlocked) ifwController.disable(component) else ifwController.enable(component)
                                    val after = !ifwController.checkComponentEnableState(component.packageName, component.name)
                                    if (!succeeded || after != target.beforeIfwBlocked) {
                                        reasons.add(IFW_FAILED)
                                    } else {
                                        changed = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.e(e, "Cannot restore IFW state for ${component.packageName}/${component.name}")
                            reasons.add(IFW_FAILED)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Cannot restore ${component.packageName}/${component.name}")
                reasons.add(ERROR)
            }
            if (reasons.isNotEmpty()) {
                failures[key] = reasons
            } else if (changed) {
                restored++
            } else {
                skipped++
            }
        }
        // Preserve retryability for operations containing a failed component.
        val completedIds = entries.groupBy { it.operationId }
            .filterValues { operationEntries -> operationEntries.none { (it.packageName to it.componentName) in failures } }
            .keys.toList()
        historyRepository.markUndone(completedIds, undoOperationId)
        grouped.keys.map { it.first }.distinct().forEach { packageName ->
            try {
                componentRepository.updateComponentList(packageName).first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Cannot refresh components for $packageName after undo")
            }
        }
        return UndoResult(
            restored = restored,
            skipped = skipped,
            failed = failures.size,
            failureReasons = failures.mapKeys { (key, _) ->
                val target = grouped.getValue(key).first()
                ComponentInfo(target.packageName, target.componentName, target.componentType)
            },
        )
    }
}
