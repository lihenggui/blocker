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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalIfwRuleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val packageManager = mock<PackageManager>()

    @Test
    fun givenEditedSimpleRuleMovedToAnotherPackage_whenSaving_thenRuleMigratesToNewFile() = runTest {
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
        val intentFirewall = StableFakeIntentFirewall().apply {
            saveRules(
                "com.old",
                IfwRules(
                    listOf(
                        IfwRule(
                            componentType = IfwComponentType.BROADCAST,
                            filters = listOf(
                                IfwFilter.ComponentFilter("com.old/com.old.BootReceiver"),
                            ),
                        ),
                    ),
                ),
            )
        }

        val viewModel = GlobalIfwRuleViewModel(
            intentFirewall = intentFirewall,
            componentRepository = componentRepository,
            packageManager = packageManager,
            savedStateHandle = SavedStateHandle(),
        )
        advanceUntilIdle()

        viewModel.openRule("com.old", 0)
        advanceUntilIdle()

        viewModel.updateSimplePackageName("com.new")
        advanceUntilIdle()
        viewModel.selectSingleTarget("com.new/com.new.BootReceiver")
        viewModel.saveRule()
        advanceUntilIdle()

        assertTrue(intentFirewall.getRules("com.old").rules.isEmpty())
        assertEquals(
            listOf(IfwFilter.ComponentFilter("com.new/com.new.BootReceiver")),
            intentFirewall.getRules("com.new").rules.single().filters,
        )
    }
}

private class StableFakeIntentFirewall : IIntentFirewall {
    private val rules = mutableMapOf<String, IfwRules>()

    override suspend fun getRules(packageName: String): IfwRules = rules[packageName] ?: IfwRules.empty()

    override suspend fun saveRules(packageName: String, rules: IfwRules) {
        if (rules.isEmpty()) {
            this.rules.remove(packageName)
        } else {
            this.rules[packageName] = rules
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
        rules.remove(packageName)
    }

    override suspend fun getAllRules(): Map<String, IfwRules> = rules.toMap()

    override fun resetCache() = Unit
}
