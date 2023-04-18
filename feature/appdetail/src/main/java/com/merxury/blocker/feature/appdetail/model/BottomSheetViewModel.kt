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

package com.merxury.blocker.feature.appdetail.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BottomSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
) : ViewModel() {
    private val appDetailArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _appDetailNavInfo: MutableStateFlow<AppDetailNavInfo> = MutableStateFlow(
        AppDetailNavInfo(
            appDetailArgs.packageName,
            appDetailArgs.tabs,
            appDetailArgs.searchKeyword,
        ),
    )
    val appDetailNavInfo: StateFlow<AppDetailNavInfo> = _appDetailNavInfo.asStateFlow()
}

data class AppDetailNavInfo(
    val packageName: String = "",
    val tab: AppDetailTabs = Info,
    val keywords: List<String> = listOf(),
)
