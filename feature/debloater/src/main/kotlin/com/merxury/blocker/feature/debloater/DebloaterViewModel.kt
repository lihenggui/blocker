/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.debloater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.debloater.DebloatableComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.asResult
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DebloaterViewModel @Inject constructor(
    debloatableComponentRepository: DebloatableComponentRepository,
    permissionMonitor: PermissionMonitor,
    private val componentRepository: ComponentRepository,
    private val userDataRepository: UserDataRepository,
    private val pm: PackageManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Error in DebloaterViewModel")
        _errorState.update { throwable.toErrorMessage() }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()

    val hasRootPermission: StateFlow<Boolean> = permissionMonitor.permissionStatus
        .map { it != PermissionStatus.NO_PERMISSION }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private var controlComponentJob: Job? = null

    private val debloatableComponentsFromRepository: StateFlow<Result<List<MatchedTarget>>> =
        combine(
            debloatableComponentRepository.getDebloatableComponent(),
            _searchQuery,
        ) { entities, query ->
            Timber.d("Received ${entities.size} debloatable components, query: $query")
            withContext(ioDispatcher) {
                groupAndFilterDebloatableComponents(entities, query)
            }
        }
            .asResult()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Result.Loading,
            )

    private val _debloatableUiState = MutableStateFlow<Result<List<MatchedTarget>>>(Result.Loading)
    val debloatableUiState = _debloatableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            debloatableComponentsFromRepository.collect { result ->
                _debloatableUiState.value = result
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.update { query }
    }

    fun controlComponent(entity: DebloatableComponentEntity, enabled: Boolean) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val componentInfo = entity.toComponentInfo()
            val controllerType = userDataRepository.userData.first().controllerType
            componentRepository.controlComponent(
                component = componentInfo,
                newState = enabled,
                controllerType = ControllerType.IFW_PLUS_PM,
            )
                .onStart {
                    changeDebloatableComponentUiStatus(
                        changed = listOf(entity),
                        controllerType = controllerType,
                        enabled = enabled,
                    )
                }
                .catch { error ->
                    Timber.e(error, "Error controlling component: ${entity.componentName}")
                    changeDebloatableComponentUiStatus(
                        changed = listOf(entity),
                        controllerType = controllerType,
                        enabled = !enabled,
                    )
                    _errorState.emit(error.toErrorMessage())
                }
                .collect { success ->
                    if (!success) {
                        Timber.w("Failed to control component: ${entity.componentName}")
                        changeDebloatableComponentUiStatus(
                            changed = listOf(entity),
                            controllerType = controllerType,
                            enabled = !enabled,
                        )
                    }
                }
        }
    }

    fun controlAllComponents(
        entities: List<DebloatableComponentEntity>,
        enable: Boolean,
    ) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val components = entities.map { it.toComponentInfo() }
            val controllerType = userDataRepository.userData.first().controllerType
            var successCount = 0
            componentRepository.batchControlComponent(
                components = components,
                newState = enable,
                controllerType = ControllerType.IFW_PLUS_PM,
            )
                .onStart {
                    changeDebloatableComponentUiStatus(
                        changed = entities,
                        controllerType = controllerType,
                        enabled = enable,
                    )
                }
                .catch { exception ->
                    Timber.e(exception, "Error batch controlling components")
                    changeDebloatableComponentUiStatus(
                        changed = entities,
                        controllerType = controllerType,
                        enabled = !enable,
                    )
                    _errorState.emit(exception.toErrorMessage())
                }
                .collect { _ ->
                    successCount++
                    Timber.v("Controlled $successCount/${components.size} components")
                }
        }
    }

    private fun changeDebloatableComponentUiStatus(
        changed: List<DebloatableComponentEntity>,
        controllerType: ControllerType,
        enabled: Boolean,
    ) {
        _debloatableUiState.update { currentState ->
            currentState.updateDebloatableSubItemSwitchState(
                changed = changed,
                controllerType = controllerType,
                enabled = enabled,
            )
        }
    }

    fun dismissError() {
        _errorState.update { null }
    }

    fun triggerTestShare(context: Context) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Test share message from Blocker")
            type = "*/*"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    private fun groupAndFilterDebloatableComponents(
        entities: List<DebloatableComponentEntity>,
        query: String,
    ): List<MatchedTarget> {
        val grouped = entities.groupBy { it.packageName }
        return grouped.mapNotNull { (packageName, debloatableComponents) ->
            val packageInfo = try {
                pm.getPackageInfoCompat(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w(e, "Package not found: $packageName")
                return@mapNotNull null
            }

            val appLabel = packageInfo?.applicationInfo?.loadLabel(pm)?.toString() ?: packageName

            val filteredTargets = if (query.isBlank()) {
                debloatableComponents
            } else {
                debloatableComponents.filter { entity ->
                    appLabel.contains(query, ignoreCase = true) ||
                        entity.componentName.contains(query, ignoreCase = true) ||
                        entity.simpleName.contains(query, ignoreCase = true) ||
                        entity.displayName.contains(query, ignoreCase = true)
                }
            }

            if (filteredTargets.isNotEmpty()) {
                val uiItems = filteredTargets.map { entity ->
                    DebloatableComponentUiItem(
                        entity = entity,
                        isShareableComponent = isShareableComponent(entity),
                        isExplicitLaunch = isExplicitLaunch(entity),
                        isLauncherEntry = isLauncherEntry(entity),
                        isDeeplinkEntry = isDeeplinkEntry(entity),
                    )
                }
                MatchedTarget(
                    header = MatchedHeaderData(
                        title = appLabel,
                        uniqueId = packageName,
                        icon = packageInfo,
                    ),
                    targets = uiItems,
                )
            } else {
                null
            }
        }
    }
}
