/*
 * Copyright 2024 Blocker
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
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.search.navigation.KEYWORD_ARG
import com.merxury.blocker.feature.search.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.search.navigation.RULE_ID_ARG
import com.merxury.blocker.feature.search.navigation.TAB_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class Search2PaneViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val selectedPackageName: StateFlow<String?> =
        savedStateHandle.getStateFlow(PACKAGE_NAME_ARG, null)
    val selectedAppTabs: StateFlow<String?> = savedStateHandle.getStateFlow(TAB_ARG, null)
    val searchKeyword: StateFlow<String?> = savedStateHandle.getStateFlow(KEYWORD_ARG, null)
    val selectedRuleId: StateFlow<String?> = savedStateHandle.getStateFlow(RULE_ID_ARG, null)
    private val _isAppDetailPage: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isAppDetailPage: StateFlow<Boolean> = _isAppDetailPage

    fun onAppClick(
        packageName: String?,
        tabs: AppDetailTabs?,
        keyword: List<String>?,
    ) {
        _isAppDetailPage.value = true
        savedStateHandle[PACKAGE_NAME_ARG] = packageName
        if (tabs != null) {
            savedStateHandle[TAB_ARG] = tabs.name
        }
        if (keyword != null) {
            savedStateHandle[KEYWORD_ARG] = keyword.joinToString(",")
        }
    }

    fun onRuleClick(ruleId: String?) {
        savedStateHandle[RULE_ID_ARG] = ruleId
        _isAppDetailPage.value = false
    }
}

