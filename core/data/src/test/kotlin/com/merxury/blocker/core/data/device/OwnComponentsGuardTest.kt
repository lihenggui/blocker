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

package com.merxury.blocker.core.data.device

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val OWN_RECEIVER = declaration("BootReceiver", ComponentType.RECEIVER, packageName = SELF_PKG)
private val OTHER_RECEIVER = declaration("Push", ComponentType.RECEIVER)

/** Blocking can never reach Blocker's own components in the user it runs in, whoever asks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OwnComponentsGuardTest : DeviceRepositoryTestBase() {
    private val sent = mutableListOf<ControlRequest>()

    /** Answers every request as achieved, recording what reached the system. */
    private fun controlAchieves() {
        componentControl.apply = { request ->
            sent += request
            flow {
                val outcomes = request.components.map { ComponentOutcome(ComponentKey(it.id, request.user), ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap()) }
                outcomes.groupBy { it.key.id.packageName }.forEach { (packageName, done) -> emit(ControlEvent.PackageDone(packageName, done)) }
                emit(ControlEvent.Finished(achieved = outcomes.size, partial = 0, failed = 0))
            }
        }
    }

    private fun request(user: UserId, goal: ControlGoal, vararg components: ComponentDeclaration) = ControlRequest(user, components.toList(), goal, setOf(ControlLayer.PACKAGE_MANAGER))

    @Test
    fun givenOwnAndOtherReceivers_whenBlockedWithIfwInTheOwnUser_thenOnlyTheOthersAreSent() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(SELF_PKG), OWN_RECEIVER)
        system.install(installedApp(PKG), OTHER_RECEIVER)
        repository.refresh(AppKey(SELF_PKG, OWNER))
        controlAchieves()

        val request = ControlRequest(OWNER, listOf(OWN_RECEIVER, OTHER_RECEIVER), ControlGoal.BLOCK, setOf(ControlLayer.INTENT_FIREWALL))
        val events = repository.control(request, operation = operation()).toList()

        assertThat(sent.single().components).containsExactly(OTHER_RECEIVER)
        val refused = events.filterIsInstance<ControlEvent.PackageDone>().single { it.packageName == SELF_PKG }.outcomes.single()
        assertThat(refused.result).isEqualTo(ControlResult.SKIPPED)
        assertThat(refused.failures).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlFailure.ProtectedComponent)
        assertThat(refused.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertThat(events.last()).isEqualTo(ControlEvent.Finished(achieved = 1, partial = 0, failed = 0, skipped = 1))
        assertThat(repository.components(AppKey(SELF_PKG, OWNER)).first().single().state.pm).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenOnlyOwnComponents_whenBlockedInTheOwnUser_thenNothingIsSent() = runTest {
        val repository = components(backgroundScope)
        controlAchieves()

        val events = repository.control(request(OWNER, ControlGoal.BLOCK, OWN_RECEIVER), operation = operation()).toList()

        assertThat(sent).isEmpty()
        assertThat(events.last()).isEqualTo(ControlEvent.Finished(achieved = 0, partial = 0, failed = 0, skipped = 1))
    }

    @Test
    fun givenOwnComponents_whenUnblockedInTheOwnUser_thenTheyAreSent() = runTest {
        val repository = components(backgroundScope)
        controlAchieves()

        repository.control(request(OWNER, ControlGoal.UNBLOCK, OWN_RECEIVER), operation = operation()).toList()

        assertThat(sent.single().components).containsExactly(OWN_RECEIVER)
    }

    @Test
    fun givenBlockerInAnotherUser_whenBlocked_thenItIsSentLikeAnyApp() = runTest {
        val repository = components(backgroundScope)
        controlAchieves()

        repository.control(request(WORK, ControlGoal.BLOCK, OWN_RECEIVER), operation = operation()).toList()

        assertThat(sent.single().components).containsExactly(OWN_RECEIVER)
    }
}
