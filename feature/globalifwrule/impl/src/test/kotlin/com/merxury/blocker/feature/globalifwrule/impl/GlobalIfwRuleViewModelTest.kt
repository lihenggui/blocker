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

import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.getOrThrow
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
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
        val intentFirewall = store(FakeIfwFileSystem())
        intentFirewall.update("com.old") { oldReceiverRules }.getOrThrow()
        val viewModel = createViewModel(intentFirewall)
        advanceUntilIdle()

        moveOldReceiverRuleToNewPackage(viewModel)

        assertTrue(intentFirewall.rules("com.old").getOrThrow().rules.isEmpty())
        assertEquals(
            listOf(IfwFilter.ComponentFilter("com.new/com.new.BootReceiver")),
            intentFirewall.rules("com.new").getOrThrow().rules.single().filters,
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
        val fs = FakeIfwFileSystem()
        val intentFirewall = store(fs)
        intentFirewall.update("com.old") { oldReceiverRules }.getOrThrow()
        intentFirewall.update("com.new") { destinationRules }.getOrThrow()
        fs.unreadablePackages += "com.new"
        val viewModel = createViewModel(intentFirewall)
        advanceUntilIdle()

        moveOldReceiverRuleToNewPackage(viewModel)

        assertEquals(oldReceiverRules, intentFirewall.rules("com.old").getOrThrow())
        fs.unreadablePackages.clear()
        assertEquals(destinationRules, intentFirewall.rules("com.new").getOrThrow())
        assertEquals(GlobalIfwRuleScreenState.SIMPLE_EDIT, viewModel.editorState.value.screen)
        assertEquals("com.new", viewModel.editorState.value.simpleDraft?.storagePackageName)
    }

    private fun store(fs: FakeIfwFileSystem) = IntentFirewallStore(fs, IfwXmlSerializer(), IfwXmlDeserializer())

    private fun createViewModel(intentFirewall: IfwStore): GlobalIfwRuleViewModel {
        val componentRepository = FakeComponentStatusRepository().apply {
            sendComponents(
                UserId.SYSTEM,
                listOf("com.old", "com.new").map { packageName ->
                    ComponentStatus(
                        ComponentDeclaration(ComponentId(packageName, "$packageName.BootReceiver"), ComponentType.RECEIVER, true, true),
                        ControlState.UNKNOWN,
                    )
                },
            )
        }
        return GlobalIfwRuleViewModel(
            ifwStore = intentFirewall,
            componentRepository = componentRepository,
            selectedUsers = FakeSelectedUserRepository(),
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
