/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.appdetail.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailUiState
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComponentDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun givenLoadedDetail_whenEditingAndSavingBeforeDispatch_thenUseTheLatestDraft() = runTest {
        val original = ComponentDetail(name = "example.app.Main", description = "Original")
        val repository = TestComponentDetailRepository().apply { sendComponentDetail(listOf(original)) }
        val viewModel = ComponentDetailViewModel(repository, StandardTestDispatcher(testScheduler), original.name)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(ComponentDetailUiState.Loading)
            assertThat(awaitItem()).isEqualTo(ComponentDetailUiState.Success(original))

            viewModel.onInfoChanged { copy(description = "Shar") }
            assertThat(viewModel.editedDetail.value?.description).isEqualTo("Shar")
            viewModel.onInfoChanged { copy(description = "ShareB") }
            viewModel.onInfoChanged { copy(disableEffect = "Sharing is unavailable") }
            val edited = original.copy(description = "ShareB", disableEffect = "Sharing is unavailable")
            assertThat(viewModel.editedDetail.value).isEqualTo(edited)

            viewModel.save(original)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(ComponentDetailUiState.Success(edited))
            assertThat(repository.getLocalComponentDetail(original.name).first()).isEqualTo(edited)
        }
    }
}
