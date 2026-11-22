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

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsCollectionObserverTest {
    private val userDataRepository = TestUserDataRepository()
    private val analyticsHelper = TestAnalyticsHelper()
    private val subject = AnalyticsCollectionObserver(userDataRepository, analyticsHelper)

    @Test
    fun whenObserverStarts_thenInitialValueIsApplied() = runTest(UnconfinedTestDispatcher()) {
        userDataRepository.sendUserData(defaultUserData.copy(enableStatistics = false))

        backgroundScope.launch { subject.observe() }

        assertThat(analyticsHelper.enableStatisticsUpdates).containsExactly(false)
    }

    @Test
    fun givenObserverRunning_whenStatisticsDisabled_thenHelperIsDisabled() = runTest(UnconfinedTestDispatcher()) {
        userDataRepository.sendUserData(defaultUserData.copy(enableStatistics = true))
        backgroundScope.launch { subject.observe() }

        userDataRepository.setEnableStatistics(false)

        assertThat(analyticsHelper.enableStatisticsUpdates).containsExactly(true, false).inOrder()
    }

    @Test
    fun givenStatisticsDisabled_whenStatisticsEnabledAgain_thenHelperIsEnabled() = runTest(UnconfinedTestDispatcher()) {
        userDataRepository.sendUserData(defaultUserData.copy(enableStatistics = true))
        backgroundScope.launch { subject.observe() }
        userDataRepository.setEnableStatistics(false)

        userDataRepository.setEnableStatistics(true)

        assertThat(analyticsHelper.enableStatisticsUpdates)
            .containsExactly(true, false, true)
            .inOrder()
    }

    @Test
    fun whenUnrelatedPreferenceChanges_thenHelperIsNotUpdatedAgain() = runTest(UnconfinedTestDispatcher()) {
        userDataRepository.sendUserData(defaultUserData.copy(enableStatistics = true))
        backgroundScope.launch { subject.observe() }

        userDataRepository.setShowSystemApps(true)

        assertThat(analyticsHelper.enableStatisticsUpdates).containsExactly(true)
    }
}
