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

package com.merxury.blocker.ui.twopane.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.merxury.blocker.feature.search.navigation.SearchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class Search2PaneViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val selectedPackageNameKey = "selectedPackageNameKey"
    private val selectedRuleIdKey = "selectedRuleIdKey"
    private val selectedAppTabKey = "selectedTabKey"
    private val searchKeywordKey = "searchKeywordKey"
    private val searchRoute: SearchRoute = savedStateHandle.toRoute()
     val selectedRuleId: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedRuleIdKey,
        initialValue = searchRoute.ruleId,
    )
     val selectedPackageName: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedPackageNameKey,
        initialValue = searchRoute.packageName,
    )
    val selectedAppTabs: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedAppTabKey,
        initialValue = searchRoute.tab,
    )
    val searchKeyword: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = searchKeywordKey,
        initialValue = searchRoute.searchKeyword.joinToString(","),
    )
    private val _isAppDetailPage: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isAppDetailPage: StateFlow<Boolean> = _isAppDetailPage

    fun onAppClick(
        packageName: String?,
        tabs: String?,
        keyword: List<String>?,
    ) {
        _isAppDetailPage.value = true
        savedStateHandle[selectedPackageNameKey] = packageName
        if (tabs != null) {
            savedStateHandle[selectedAppTabKey] = tabs
        }
        if (keyword != null) {
            savedStateHandle[selectedPackageNameKey] = keyword.joinToString(",")
        }
    }

    fun onRuleClick(ruleId: String?) {
        savedStateHandle[selectedRuleIdKey] = ruleId
        _isAppDetailPage.value = false
    }
}
