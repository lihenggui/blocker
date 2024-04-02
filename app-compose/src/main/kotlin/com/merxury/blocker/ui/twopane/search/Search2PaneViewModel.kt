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
import com.merxury.blocker.feature.search.navigation.AppDetailArgs
import com.merxury.blocker.feature.search.navigation.IS_APP_DETAIL_PAGE
import com.merxury.blocker.feature.search.navigation.KEYWORD_ARG
import com.merxury.blocker.feature.search.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.search.navigation.RULE_ID_ARG
import com.merxury.blocker.feature.search.navigation.TAB_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class Search2PaneViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val appDetailArgs: AppDetailArgs = AppDetailArgs(savedStateHandle)
    val isAppDetailPage: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(IS_APP_DETAIL_PAGE, true)
    val selectedRuleId: StateFlow<String?> = savedStateHandle.getStateFlow(RULE_ID_ARG, null)
    private val _search2PaneState = MutableStateFlow(Search2PaneState())
    val search2PaneState: StateFlow<Search2PaneState> = _search2PaneState

    init {
        loadState()
    }

    private fun loadState() {
        _search2PaneState.update {
            Search2PaneState(
                selectedPackageName = appDetailArgs.packageName,
                selectedAppTabs = AppDetailTabs.fromName(appDetailArgs.tabs),
                searchKeyword = appDetailArgs.searchKeyword.split(",")
            )
        }
    }


    fun onRuleClick(ruleId: String?) {
        savedStateHandle[RULE_ID_ARG] = ruleId
    }

    fun onAppClick(
        packageName: String?,
        tabs: AppDetailTabs?,
        keyword: List<String>?,
    ) {
        savedStateHandle[PACKAGE_NAME_ARG] = packageName
        if (tabs != null) {
            savedStateHandle[TAB_ARG] = tabs.name
        }
        if (keyword != null) {
            savedStateHandle[KEYWORD_ARG] = keyword.joinToString(",")
        }
    }
}

data class Search2PaneState(
    val selectedPackageName: String? = null,
    val selectedAppTabs: AppDetailTabs? = AppDetailTabs.Info,
    val searchKeyword: List<String>? = listOf(),
)

