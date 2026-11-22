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

package com.merxury.blocker.feature.globalifwrule.impl

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.mockito.kotlin.mock
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalIfwRuleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val packageManager = mock<PackageManager>()

    private val oldReceiverRules = IfwRules(
        listOf(
            IfwRule(
                componentType = IfwComponentType.BROADCAST,
                filters = listOf(
                    IfwFilter.ComponentFilter("com.old/com.old.BootReceiver"),
                ),
            ),
        ),
    )

    @Test
    fun givenEditedSimpleRuleMovedToAnotherPackage_whenSaving_thenRuleMigratesToNewFile() = runTest {
        val intentFirewall = StableFakeIntentFirewall().apply {
            saveRules("com.old", oldReceiverRules)
        }
        val viewModel = createViewModel(intentFirewall)
        advanceUntilIdle()

        moveOldReceiverRuleToNewPackage(viewModel)

        assertTrue(intentFirewall.getRules("com.old").rules.isEmpty())
        assertEquals(
            listOf(IfwFilter.ComponentFilter("com.new/com.new.BootReceiver")),
            intentFirewall.getRules("com.new").rules.single().filters,
        )
    }

    @Test
    fun givenUnreadableDestination_whenMovingRule_thenRuleStaysInSourceAndEditorStaysOpen() = runTest {
        val destinationRules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.SERVICE,
                    filters = listOf(IfwFilter.ComponentFilter("com.new/com.new.SyncService")),
                ),
            ),
        )
        val intentFirewall = StableFakeIntentFirewall().apply {
            saveRules("com.old", oldReceiverRules)
            saveRules("com.new", destinationRules)
            unreadablePackages += "com.new"
        }
        val viewModel = createViewModel(intentFirewall)
        advanceUntilIdle()

        moveOldReceiverRuleToNewPackage(viewModel)

        assertEquals(oldReceiverRules, intentFirewall.storedRules["com.old"])
        assertEquals(destinationRules, intentFirewall.storedRules["com.new"])
        assertEquals(GlobalIfwRuleScreenState.SIMPLE_EDIT, viewModel.editorState.value.screen)
        assertEquals("com.new", viewModel.editorState.value.simpleDraft?.storagePackageName)
    }

    private fun createViewModel(intentFirewall: IIntentFirewall): GlobalIfwRuleViewModel {
        val componentRepository = TestComponentRepository().apply {
            sendComponentList(
                listOf(
                    ComponentInfo(
                        packageName = "com.old",
                        name = "com.old.BootReceiver",
                        type = ComponentType.RECEIVER,
                    ),
                    ComponentInfo(
                        packageName = "com.new",
                        name = "com.new.BootReceiver",
                        type = ComponentType.RECEIVER,
                    ),
                ),
            )
        }
        return GlobalIfwRuleViewModel(
            intentFirewall = intentFirewall,
            componentRepository = componentRepository,
            packageManager = packageManager,
            savedStateHandle = SavedStateHandle(),
        )
    }

    private fun TestScope.moveOldReceiverRuleToNewPackage(viewModel: GlobalIfwRuleViewModel) {
        viewModel.openRule("com.old", 0)
        advanceUntilIdle()

        viewModel.updateSimplePackageName("com.new")
        advanceUntilIdle()
        viewModel.selectSingleTarget("com.new/com.new.BootReceiver")
        viewModel.saveRule()
        advanceUntilIdle()
    }
}

/** Stores rules in memory. Like IntentFirewall, getRules() throws for a file it cannot read. */
private class StableFakeIntentFirewall : IIntentFirewall {
    val storedRules = mutableMapOf<String, IfwRules>()
    val unreadablePackages = mutableSetOf<String>()

    override suspend fun getRules(packageName: String): IfwRules {
        if (packageName in unreadablePackages) throw IOException("Cannot read IFW rules for $packageName")
        return storedRules[packageName] ?: IfwRules.empty()
    }

    override suspend fun saveRules(packageName: String, rules: IfwRules) {
        if (rules.isEmpty()) {
            storedRules.remove(packageName)
        } else {
            storedRules[packageName] = rules
        }
    }

    override suspend fun addComponentFilter(packageName: String, componentName: String): Boolean = true

    override suspend fun removeComponentFilter(packageName: String, componentName: String): Boolean = true

    override suspend fun addAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) = Unit

    override suspend fun removeAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) = Unit

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = true

    override suspend fun clear(packageName: String) {
        storedRules.remove(packageName)
    }

    override suspend fun getAllRules(): Map<String, IfwRules> = storedRules.filterKeys { it !in unreadablePackages }

    override fun resetCache() = Unit
}
