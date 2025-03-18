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
import androidx.navigation.toRoute
import com.merxury.blocker.feature.applist.navigation.AppListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppList2PaneViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val selectedPackageKey = "selectedPackageKey"
    private val appListRoute: AppListRoute = savedStateHandle.toRoute()
    val selectedPackageName: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedPackageKey,
        initialValue = appListRoute.initialPackageName,
    )

    fun onAppClick(packageName: String?) {
        savedStateHandle[selectedPackageKey] = packageName
    }
}
