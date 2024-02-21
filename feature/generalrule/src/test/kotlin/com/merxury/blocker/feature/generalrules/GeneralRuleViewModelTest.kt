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

package com.merxury.blocker.feature.generalrules

import android.content.Context
import com.merxury.blocker.core.domain.InitializeRuleStorageUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.UpdateRuleMatchedAppUseCase
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

class GeneralRuleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder()
        .assureDeletion()
        .build()

    private val appRepository = TestAppRepository()
    private val appPropertiesRepository = TestAppPropertiesRepository()
    private val generalRuleRepository = TestGeneralRuleRepository()
    private val userDataRepository = TestUserDataRepository()
    private val componentRepository = TestComponentRepository()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher

    private val context = mock<Context>()

    @Before
    fun setup() {
        val initGeneralRuleUseCase = InitializeRuleStorageUseCase(
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = tempFolder.newFolder().absolutePath,
            ioDispatcher = dispatcher,
            appContext = context,
        )
        val searchRule = SearchGeneralRuleUseCase(
            generalRuleRepository = generalRuleRepository,
            userDataRepository = userDataRepository,
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = tempFolder.newFolder().absolutePath,
        )
        val updateRule = UpdateRuleMatchedAppUseCase(
            generalRuleRepository = generalRuleRepository,
            appRepository = appRepository,
            userDataRepository = userDataRepository,
            componentRepository = componentRepository,
            ioDispatcher = dispatcher,
        )

        val viewModel = GeneralRulesViewModel(
            appRepository = appRepository,
            appPropertiesRepository = appPropertiesRepository,
            generalRuleRepository = generalRuleRepository,
            initGeneralRuleUseCase = initGeneralRuleUseCase,
            searchRule = searchRule,
            updateRule = updateRule,
            ioDispatcher = dispatcher,
        )
    }
}