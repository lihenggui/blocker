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

package com.merxury.blocker.ui.twopane.applist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.merxury.blocker.feature.applist.navigation.PACKAGE_NAME_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppList2PaneViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val selectedPackageName: StateFlow<String?> = savedStateHandle.getStateFlow(PACKAGE_NAME_ARG, savedStateHandle[PACKAGE_NAME_ARG])

    fun onAppClick(packageName: String?) {
        savedStateHandle[PACKAGE_NAME_ARG] = packageName
    }
}
