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

package com.merxury.blocker.feature.navigation.impl

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.domain.GatherAllMatchedComponentsUseCase
import com.merxury.blocker.core.domain.InitializeRuleStorageUseCase
import com.merxury.blocker.core.domain.MatchGeneralRulesUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.UpdateRuleMatchedAppsUseCase
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.preference.AppPropertiesData
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.feature.generalrule.impl.GeneralRulesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GeneralRuleBatchProgressTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val componentData = TestComponentRepository()
    private val rules = TestGeneralRuleRepository()
    private val apps = TestAppRepository()
    private val users = TestUserDataRepository()
    private val properties = TestAppPropertiesRepository()
    private val components = List(3) { ComponentInfo("com.example", "com.tracker.Service$it", SERVICE) }
    private var gatherGate: CompletableDeferred<Unit>? = null
    private var commitGate: CompletableDeferred<Unit>? = null
    private var snapshotFailure: Exception? = null
    private var controlCalls = 0
    private val componentRepository = object : ComponentRepository by componentData {
        override fun getAllComponents(): Flow<List<ComponentInfo>> = flow {
            gatherGate?.await()
            snapshotFailure?.let { throw it }
            emitAll(componentData.getAllComponents())
        }
        override fun batchControlComponent(
            components: List<ComponentInfo>,
            newState: Boolean,
            controllerType: ControllerType?,
        ): Flow<ComponentInfo> = flow {
            controlCalls++
            components.forEach { emit(it) }
            commitGate?.await()
        }
    }
    private lateinit var viewModel: GeneralRulesViewModel

    @Before
    fun setup() {
        val rulesFolder = folder.newFolder("rules")
        rulesFolder.resolve("general.json").writeText("[]")
        users.sendUserData(defaultUserData)
        apps.sendAppList(listOf(InstalledApp(packageName = "com.example", label = "Example")))
        componentData.sendComponentList(components)
        rules.sendRuleList(listOf(GeneralRule(1, "Tracker", searchKeyword = listOf("com.tracker"))))
        properties.sendAppProperties(AppPropertiesData(componentDatabaseInitialized = true, generalRuleDatabaseInitialized = true))
        val matcher = MatchGeneralRulesUseCase(componentRepository, apps, users, dispatcher.testDispatcher)
        viewModel = GeneralRulesViewModel(
            appRepository = apps,
            appPropertiesRepository = properties,
            generalRuleRepository = rules,
            componentRepository = componentRepository,
            initGeneralRuleUseCase = InitializeRuleStorageUseCase(mock<WorkManager>(), folder.root, rulesFolder.absolutePath, users, dispatcher.testDispatcher),
            searchRule = SearchGeneralRuleUseCase(rules, users, folder.root, rulesFolder.absolutePath),
            updateRules = UpdateRuleMatchedAppsUseCase(rules, matcher),
            gatherAllMatchedComponents = GatherAllMatchedComponentsUseCase(rules, matcher),
            analyticsHelper = TestAnalyticsHelper(),
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher.testDispatcher,
            initialRuleId = "",
        )
    }

    @Test
    fun gatheringMarksProcessingImmediatelyAndIgnoresDuplicateClicks() = runTest {
        runCurrent()
        gatherGate = CompletableDeferred()
        viewModel.controlAllComponents(false)
        viewModel.controlAllComponents(true)
        assertTrue(viewModel.isProcessing.value)
        assertFalse(assertNotNull(viewModel.processingProgress.value).isComplete)
        assertEquals(0, controlCalls)

        gatherGate!!.complete(Unit)
        runCurrent()

        assertEquals(1, controlCalls)
        assertFalse(viewModel.isProcessing.value)
        assertTrue(assertNotNull(viewModel.processingProgress.value).isComplete)
    }

    @Test
    fun lastComponentCallbackDoesNotCompleteBeforeReconciliation() = runTest {
        runCurrent()
        commitGate = CompletableDeferred()
        viewModel.controlAllComponents(false)
        runCurrent()
        val pending = assertNotNull(viewModel.processingProgress.value)
        assertEquals(3, pending.current)
        assertEquals(3, pending.total)
        assertFalse(pending.isComplete)
        assertTrue(viewModel.isProcessing.value)

        commitGate!!.complete(Unit)
        runCurrent()

        assertTrue(assertNotNull(viewModel.processingProgress.value).isComplete)
        assertFalse(viewModel.isProcessing.value)
    }

    @Test
    fun gatheringFailureClearsProgressAndRestoresActions() = runTest {
        runCurrent()
        snapshotFailure = IllegalStateException("snapshot failed")
        viewModel.controlAllComponents(false)
        runCurrent()
        assertNotNull(viewModel.errorState.value)
        assertNull(viewModel.processingProgress.value)
        assertFalse(viewModel.isProcessing.value)
        assertEquals(0, controlCalls)
    }
}
