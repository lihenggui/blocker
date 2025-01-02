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

package com.merxury.blocker.feature.ruledetail

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.materialkolor.ktx.themeColorOrNull
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.extension.updateComponentInfoSwitchState
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Applicable
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Description
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.ruledetail.navigation.RuleIdArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RuleDetailViewModel @Inject constructor(
    private val appContext: Application,
    savedStateHandle: SavedStateHandle,
    private val pm: PackageManager,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    private val ruleRepository: GeneralRuleRepository,
    private val appRepository: AppRepository,
    private val userDataRepository: UserDataRepository,
    private val componentRepository: ComponentRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {
    private val ruleIdArgs: RuleIdArgs = RuleIdArgs(savedStateHandle)
    private val _ruleInfoUiState: MutableStateFlow<RuleInfoUiState> =
        MutableStateFlow(RuleInfoUiState.Loading)
    val ruleInfoUiState: StateFlow<RuleInfoUiState> = _ruleInfoUiState
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }

    private val _tabState = MutableStateFlow(
        TabState(
            items = listOf(
                Applicable,
                Description,
            ),
            selectedItem = Applicable,
        ),
    )
    val tabState: StateFlow<TabState<RuleDetailTabs>> = _tabState.asStateFlow()
    private val _appBarUiState = MutableStateFlow(AppBarUiState(actions = getAppBarAction()))
    val appBarUiState: StateFlow<AppBarUiState> = _appBarUiState.asStateFlow()
    private var currentSearchKeyword: List<String> = emptyList()
    private var loadRuleDetailJob: Job? = null
    private var controlComponentJob: Job? = null

    init {
        loadTabInfo()
        loadData()
    }

    @VisibleForTesting
    fun loadData() {
        loadRuleDetailJob?.cancel()
        loadRuleDetailJob = viewModelScope.launch {
            val ruleId = ruleIdArgs.ruleId
            val rule = ruleRepository.getGeneralRule(ruleId)
                .first()
            val iconFile = withContext(ioDispatcher) {
                val iconUrl = rule.iconUrl ?: return@withContext null
                File(filesDir, ruleBaseFolder)
                    .resolve(iconUrl)
            }
            val ruleWithIcon = rule.copy(iconUrl = iconFile?.absolutePath)
            _ruleInfoUiState.update {
                RuleInfoUiState.Success(
                    ruleInfo = ruleWithIcon,
                    matchedAppsUiState = Result.Loading,
                )
            }
            updateSeedColor(iconFile)
            currentSearchKeyword = rule.searchKeyword
            loadMatchedApps(rule.searchKeyword)
        }
    }

    private fun updateSeedColor(iconFile: File?) = viewModelScope.launch(ioDispatcher) {
        if (iconFile == null) return@launch
        val useDynamicColor = userDataRepository.userData.first().useDynamicColor
        if (!useDynamicColor) return@launch
        _ruleInfoUiState.update {
            if (it !is RuleInfoUiState.Success) return@update it
            val seedColor = getSeedColor(iconFile, context = appContext)
            it.copy(seedColor = seedColor)
        }
    }

    fun controlAllComponentsInPage(enable: Boolean, action: (Int, Int) -> Unit) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch {
            analyticsHelper.logControlAllInPageClicked(newState = enable)
            // Make sure that the user is in the correct state
            val ruleUiList = _ruleInfoUiState.value
            if (ruleUiList !is RuleInfoUiState.Success) {
                Timber.e("Rule info is not ready")
                return@launch
            }
            val matchedAppState = ruleUiList.matchedAppsUiState
            if (matchedAppState !is Result.Success) {
                Timber.e("Matched app list is not ready")
                return@launch
            }
            val list = matchedAppState.data
                .flatMap { it.componentList }
            controlAllComponentsInternal(list, enable, action)
        }
    }

    fun controlAllComponents(
        list: List<ComponentInfo>,
        enable: Boolean,
        action: (Int, Int) -> Unit,
    ) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch {
            analyticsHelper.logControlAllComponentsClicked(newState = enable)
            controlAllComponentsInternal(list, enable, action)
        }
    }

    private suspend fun controlAllComponentsInternal(
        list: List<ComponentInfo>,
        enable: Boolean,
        action: suspend (Int, Int) -> Unit,
    ) {
        val controllerType = userDataRepository.userData.first().controllerType
        var successCount = 0
        componentRepository.batchControlComponent(
            components = list,
            newState = enable,
        )
            .onStart {
                changeComponentUiStatus(list, controllerType, enable)
            }
            .catch { exception ->
                changeComponentUiStatus(list, controllerType, !enable)
                _errorState.emit(exception.toErrorMessage())
            }
            .collect { _ ->
                successCount++
                action(successCount, list.size)
            }
    }

    private suspend fun loadMatchedApps(keywords: List<String>) {
        val matchedComponents = mutableListOf<ComponentInfo>()
        for (keyword in keywords) {
            val components = componentRepository.searchComponent(keyword).first()
            matchedComponents.addAll(components)
        }
        Timber.v("Find ${matchedComponents.size} matched components for rule: $keywords")
        val showSystemApps = userDataRepository.userData.first().showSystemApps
        val searchResult = matchedComponents.groupBy { it.packageName }
            .mapNotNull { (packageName, components) ->
                val app = appRepository.getApplication(packageName).first()
                    ?: return@mapNotNull null
                if (!showSystemApps && app.isSystem) return@mapNotNull null
                val packageInfo = pm.getPackageInfoCompat(packageName, 0)
                val headerData = MatchedHeaderData(
                    title = app.label,
                    uniqueId = packageName,
                    icon = packageInfo,
                )
                val searchedComponentInfo = components
                    .toSet() // Remove duplicate components caused by multiple keywords
                    .toList()
                MatchedItem(headerData, searchedComponentInfo)
            }
        withContext(mainDispatcher) {
            _ruleInfoUiState.update {
                val matchedApps = Result.Success(searchResult)
                if (it is RuleInfoUiState.Success) {
                    it.copy(matchedAppsUiState = matchedApps)
                } else {
                    // Unreachable code
                    Timber.e("Updating matched apps when rule info is not ready")
                    RuleInfoUiState.Error(UiMessage("Wrong UI state"))
                }
            }
        }
    }

    fun switchTab(newTab: RuleDetailTabs) {
        if (newTab != tabState.value.selectedItem) {
            _tabState.update {
                it.copy(selectedItem = newTab)
            }
            _appBarUiState.update {
                it.copy(actions = getAppBarAction())
            }
        }
    }

    private fun getAppBarAction(): List<AppBarAction> = when (tabState.value.selectedItem) {
        Description -> listOf()
        else -> listOf(MORE)
    }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun launchActivity(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am start -n $packageName/$componentName".exec(ioDispatcher)
            analyticsHelper.logLaunchActivityClicked()
        }
    }

    fun stopService(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am stopservice $packageName/$componentName".exec(ioDispatcher)
            analyticsHelper.logStopServiceClicked()
        }
    }

    fun controlComponent(
        component: ComponentInfo,
        enabled: Boolean,
    ) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            controlComponentInternal(component, enabled)
            analyticsHelper.logSwitchComponentStateClicked(newState = enabled)
        }
    }

    private fun changeComponentUiStatus(
        changed: List<ComponentInfo>,
        controllerType: ControllerType,
        enable: Boolean,
    ) {
        val currentUiState = _ruleInfoUiState.value
        if (currentUiState !is RuleInfoUiState.Success) {
            Timber.e("Cannot control component when rule info is not ready")
            return
        }
        val newState = currentUiState.matchedAppsUiState.updateComponentInfoSwitchState(
            changed = changed,
            controllerType = controllerType,
            enabled = enable,
        )
        _ruleInfoUiState.update {
            currentUiState.copy(matchedAppsUiState = newState)
        }
    }

    private suspend fun controlComponentInternal(
        component: ComponentInfo,
        enabled: Boolean,
    ) {
        val controllerType = userDataRepository.userData.first().controllerType
        componentRepository.controlComponent(component, enabled)
            .onStart {
                changeComponentUiStatus(listOf(component), controllerType, enabled)
            }
            .catch { exception ->
                _errorState.emit(exception.toErrorMessage())
                changeComponentUiStatus(listOf(component), controllerType, !enabled)
            }
            .collect { result ->
                if (!result) {
                    changeComponentUiStatus(listOf(component), controllerType, !enabled)
                }
            }
    }

    private fun loadTabInfo() {
        val screen = ruleIdArgs.tabs
        Timber.v("Jump to tab: $screen")
        _tabState.update { it.copy(selectedItem = screen) }
    }

    private suspend fun getSeedColor(icon: File?, context: Context): Color? = withContext(ioDispatcher) {
        val useDynamicColor = userDataRepository.userData.first().useDynamicColor
        if (!useDynamicColor) return@withContext null
        val request = ImageRequest.Builder(context)
            .data(icon)
            // We scale the image to cover 128px x 128px (i.e. min dimension == 128px)
            .size(128).scale(Scale.FILL)
            // Disable hardware bitmaps, since Palette uses Bitmap.getPixels()
            .allowHardware(false)
            // Set a custom memory cache key to avoid overwriting the displayed image in the cache
            .memoryCacheKey("$icon.palette")
            .build()

        val bitmap = when (val result = context.imageLoader.execute(request)) {
            is SuccessResult -> result.drawable.toBitmap()
            else -> null
        }
        return@withContext bitmap?.asImageBitmap()
            ?.themeColorOrNull()
    }
}

sealed interface RuleInfoUiState {
    data object Loading : RuleInfoUiState
    class Error(val error: UiMessage) : RuleInfoUiState
    data class Success(
        val ruleInfo: GeneralRule,
        val matchedAppsUiState: Result<List<MatchedItem>>,
        val seedColor: Color? = null,
    ) : RuleInfoUiState
}
