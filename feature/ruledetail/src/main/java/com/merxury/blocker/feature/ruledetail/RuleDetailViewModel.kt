/*
 * Copyright 2023 Blocker
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
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.model.data.toComponentItem
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Applicable
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Description
import com.merxury.blocker.core.ui.rule.RuleMatchedApp
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState.Loading
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState.Success
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.ruledetail.RuleInfoUiState.Success
import com.merxury.blocker.feature.ruledetail.navigation.RuleIdArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RuleDetailViewModel @Inject constructor(
    private val appContext: Application,
    savedStateHandle: SavedStateHandle,
    private val pm: PackageManager,
    private val ruleRepository: GeneralRuleRepository,
    private val appRepository: AppRepository,
    private val userDataRepository: UserDataRepository,
    private val componentRepository: ComponentRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val shizukuInitializer: ShizukuInitializer,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {
    private val ruleIdArgs: RuleIdArgs = RuleIdArgs(savedStateHandle)
    private val _ruleMatchedAppListUiState: MutableStateFlow<RuleMatchedAppListUiState> =
        MutableStateFlow(Loading)
    val ruleMatchedAppListUiState: StateFlow<RuleMatchedAppListUiState> =
        _ruleMatchedAppListUiState
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

    init {
        loadTabInfo()
        loadData()
    }

    override fun onCleared() {
        super.onCleared()
        deinitShizuku()
    }

    fun initShizuku() = viewModelScope.launch {
        val controllerType = userDataRepository.userData.first().controllerType
        if (controllerType == SHIZUKU) {
            shizukuInitializer.registerShizuku()
        }
    }

    private fun deinitShizuku() = viewModelScope.launch {
        val controllerType = userDataRepository.userData.first().controllerType
        if (controllerType == SHIZUKU) {
            shizukuInitializer.unregisterShizuku()
        }
    }

    private fun loadData() = viewModelScope.launch {
        val context: Context = appContext
        val ruleId = ruleIdArgs.ruleId
        val baseUrl = userDataRepository.userData
            .first()
            .ruleServerProvider
            .baseUrl
        val rule = ruleRepository.getGeneralRule(ruleId)
            .first()
        val ruleWithIcon = rule.copy(iconUrl = baseUrl + rule.iconUrl)
        _ruleInfoUiState.update {
            Success(
                ruleInfo = ruleWithIcon,
                ruleIcon = getRuleIcon(baseUrl + rule.iconUrl, context = context),
            )
        }
        currentSearchKeyword = rule.searchKeyword
        loadMatchedApps(rule.searchKeyword)
    }

    fun controlAllComponentsInPage(enable: Boolean) = viewModelScope.launch {
        val uiState = _ruleMatchedAppListUiState.value as? Success
            ?: return@launch
        val list = uiState.list
            .flatMap { it.componentList }
        controlAllComponents(list, enable)
        analyticsHelper.logControlAllInPageClicked(newState = enable)
    }

    fun controlAllComponents(list: List<ComponentItem>, enable: Boolean) = viewModelScope.launch {
        list.forEach {
            controlComponentInternal(it.packageName, it.name, enable)
        }
        loadMatchedApps(currentSearchKeyword)
        analyticsHelper.logControlAllComponentsClicked(newState = enable)
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
                val app =
                    appRepository.getApplication(packageName).first() ?: return@mapNotNull null
                if (!showSystemApps && app.isSystem) return@mapNotNull null
                val packageInfo = pm.getPackageInfoCompat(packageName, 0)
                val appItem = app.toAppItem(packageInfo = packageInfo)
                val searchedComponentItem = components.map { it.toComponentItem() }
                RuleMatchedApp(appItem, searchedComponentItem)
            }
        _ruleMatchedAppListUiState.emit(Success(searchResult))
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
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        controlComponentInternal(packageName, componentName, enabled)
        loadMatchedApps(currentSearchKeyword)
        analyticsHelper.logSwitchComponentStateClicked(newState = enabled)
    }

    private suspend fun controlComponentInternal(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) {
        componentRepository.controlComponent(packageName, componentName, enabled)
            .catch { exception ->
                _errorState.emit(exception.toErrorMessage())
            }
            .collect()
    }

    private fun loadTabInfo() {
        val screen = ruleIdArgs.tabs
        Timber.v("Jump to tab: $screen")
        _tabState.update { it.copy(selectedItem = screen) }
    }

    private suspend fun getRuleIcon(iconUrl: String?, context: Context) =
        withContext(ioDispatcher) {
            val request = ImageRequest.Builder(context)
                .data(iconUrl)
                // We scale the image to cover 128px x 128px (i.e. min dimension == 128px)
                .size(128).scale(Scale.FILL)
                // Disable hardware bitmaps, since Palette uses Bitmap.getPixels()
                .allowHardware(false)
                // Set a custom memory cache key to avoid overwriting the displayed image in the cache
                .memoryCacheKey("$iconUrl.palette")
                .build()

            val bitmap = when (val result = context.imageLoader.execute(request)) {
                is SuccessResult -> result.drawable.toBitmap()
                else -> null
            }
            return@withContext bitmap
        }
}

sealed interface RuleInfoUiState {
    data object Loading : RuleInfoUiState
    class Error(val error: UiMessage) : RuleInfoUiState
    data class Success(
        val ruleInfo: GeneralRule,
        val ruleIcon: Bitmap?,
    ) : RuleInfoUiState
}
