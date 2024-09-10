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

import com.merxury.blocker.core.data.respository.licenses.LicensesRepository
import com.merxury.blocker.core.model.data.LicenseGroup
import com.merxury.blocker.core.model.licenses.LicenseItem
import com.merxury.blocker.core.model.licenses.Scm
import com.merxury.blocker.core.model.licenses.SpdxLicense
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.licenses.LicensesUiState
import com.merxury.blocker.feature.licenses.LicensesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class LicensesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var licensesRepository: LicensesRepository
    private lateinit var viewModel: LicensesViewModel

    private val testLicenses = listOf(
        LicenseItem(
            groupId = "androidx.activity",
            artifactId = "activity",
            version = "1.9.1",
            name = "Activity",
            spdxLicenses = listOf(
                SpdxLicense(
                    identifier = "Apache-2.0",
                    name = "Apache License 2.0",
                    url = "https://www.apache.org/licenses/LICENSE-2.0",
                ),
            ),
            scm = Scm(url = "https://cs.android.com/androidx/platform/frameworks/support"),
        ),
        LicenseItem(
            groupId = "androidx.activity",
            artifactId = "activity-compose",
            version = "1.9.1",
            name = "Activity Compose",
            spdxLicenses = listOf(
                SpdxLicense(
                    identifier = "Apache-2.0",
                    name = "Apache License 2.0",
                    url = "https://www.apache.org/licenses/LICENSE-2.0",
                ),
            ),
            scm = Scm(url = "https://cs.android.com/androidx/platform/frameworks/support"),
        ),
    )

    @Before
    fun setUp() {
        licensesRepository = mock()
        val licensesFlow: StateFlow<List<LicenseItem>> = MutableStateFlow(testLicenses)
        `when`(licensesRepository.getLicensesList()).thenReturn(licensesFlow)
        viewModel = LicensesViewModel(licensesRepository)
    }

    @Test
    fun givenLicenses_whenFetch_thenReturnsSuccessState() = runTest {
        val expectedGroups = listOf(
            LicenseGroup(
                id = "androidx.activity",
                artifacts = testLicenses,
            ),
        )

        val state = viewModel.licensesUiState.first { it is LicensesUiState.Success }
        assertTrue(state is LicensesUiState.Success)
        assertEquals(expectedGroups, state.licenses)
    }

    @Test
    fun givenEmptyLicenses_whenFetch_thenReturnsSuccessStateWithEmptyList() = runTest {
        val emptyLicensesFlow: StateFlow<List<LicenseItem>> = MutableStateFlow(emptyList())
        `when`(licensesRepository.getLicensesList()).thenReturn(emptyLicensesFlow)
        viewModel = LicensesViewModel(licensesRepository)

        val state = viewModel.licensesUiState.first { it is LicensesUiState.Success }
        assertTrue(state is LicensesUiState.Success)
        assertEquals(emptyList(), state.licenses)
    }
}
