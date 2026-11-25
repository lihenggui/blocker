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

package com.merxury.blocker

import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Keeps [AnalyticsHelper] in line with the user's statistics preference.
 *
 * The preference is applied when [observe] starts and again on every change, so toggling it in
 * Settings takes effect right away instead of at the next process start.
 */
class AnalyticsCollectionObserver @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
) {
    /** Collects the preference until the calling coroutine is cancelled. */
    suspend fun observe() {
        userDataRepository.userData
            .map { it.enableStatistics }
            .distinctUntilChanged()
            .collect { analyticsHelper.setEnableStatistics(it) }
    }
}
