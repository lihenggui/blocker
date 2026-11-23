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

package com.merxury.blocker.core.domain.wake

import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.ReviewedWakePaths
import com.merxury.blocker.core.data.wake.WakeComponentOwnership
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.data.wake.WakeImport
import com.merxury.blocker.core.data.wake.WakeProtectionRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.WakeAccess
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.core.ifw.ManagedWakeRules
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The wake feature and the user's own block of the same component, in both orders. The user's
 * block is what the app records in `pm_block_record`; the component states are a fake system.
 */
class WakeUserBlockTest {
    private val repository = mock<WakeRepository>()
    private val ownership = mock<WakeComponentOwnership>()
    private val catalogue = mock<ReviewedWakePaths>()
    private val rules = mock<ManagedWakeRules>()
    private val system = FakeComponentStates()
    private val userBlocks = FakePmBlockRecordDao()
    private val TestScope.manager get() = WakeProtectionManager(
        repository,
        WakeProtectionRepository(repository, ownership, catalogue, rules, system, FakePrivilegedChannel(), userBlocks),
        StandardTestDispatcher(testScheduler),
    )

    // A reviewed, dedicated component the wake feature may disable.
    private val candidate = WakeProtection(
        "id", "target", "target", "target.Wake", "", "proof", false, false,
        method = "PM", previousState = null, pendingChange = false,
    )
    private val claimed = candidate.copy(configured = true, previousState = COMPONENT_ENABLED_STATE_DEFAULT)

    private suspend fun reconcileWith(previous: List<WakeProtection>, qualifying: List<WakeProtection>) {
        whenever(repository.isOwnerProfile).thenReturn(true)
        whenever(repository.access()).thenReturn(WakeAccess.DIRECT_ROOT)
        whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), listOf(0)))
        whenever(repository.firewallResets()).thenReturn(emptyMap())
        whenever(repository.protections()).thenReturn(previous)
        whenever(repository.attributionRecords("boot")).thenReturn(emptyList())
        whenever(repository.observedComponents(any())).thenReturn(emptyList())
        whenever(repository.detectionEvidence(any(), any())).thenReturn(WakeDetectionEvidence(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()))
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(qualifying)
        whenever(catalogue.isCurrent(any())).thenReturn(true)
        whenever(catalogue.sameInstallation(any())).thenReturn(true)
        whenever(catalogue.currentState(any())).thenAnswer { (it.arguments[0] as WakeProtection).let { path -> system.setting(path.target, path.component) } }
    }

    @Test fun givenWakeDisabledThenUserBlocked_whenPathRetires_thenComponentStaysDisabledAndClaimIsDropped() = runTest {
        reconcileWith(previous = emptyList(), qualifying = listOf(candidate))
        assertThat(manager.reconcile()).isTrue()
        assertThat(system.setting("target", "target.Wake")).isEqualTo(COMPONENT_ENABLED_STATE_DISABLED)
        verify(ownership).save(claimed)

        // The user blocks it through Blocker; it reads DISABLED already and the app records the block.
        userBlocks.userBlocked("target", "target.Wake")
        reconcileWith(previous = listOf(claimed), qualifying = emptyList())
        assertThat(manager.reconcile()).isTrue()

        assertThat(system.setting("target", "target.Wake")).isEqualTo(COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(system.changes).containsExactly("target/target.Wake 0 0->2")
        verify(ownership).save(claimed.copy(configured = false, pendingChange = false))
    }

    @Test fun givenUserBlockedThenWakeQualifies_whenReconcilingAndRetiring_thenWakeNeverTouchesIt() = runTest {
        system.put("target", "target.Wake", COMPONENT_ENABLED_STATE_DISABLED)
        userBlocks.userBlocked("target", "target.Wake")

        reconcileWith(previous = emptyList(), qualifying = listOf(candidate))
        assertThat(manager.reconcile()).isTrue()
        reconcileWith(previous = listOf(candidate), qualifying = emptyList())
        assertThat(manager.reconcile()).isTrue()

        assertThat(system.setting("target", "target.Wake")).isEqualTo(COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(system.changes).isEmpty()
        verify(ownership, never()).save(any())
    }

    @Test fun givenWakeDisabledAndUserDidNotBlock_whenPathRetires_thenOriginalStateIsRestored() = runTest {
        system.put("target", "target.Wake", COMPONENT_ENABLED_STATE_DISABLED)
        // A block of another component, or of the same one for another user, is not this one.
        userBlocks.userBlocked("target", "target.Other")
        userBlocks.userBlocked("target", "target.Wake", UserId(10))

        reconcileWith(previous = listOf(claimed), qualifying = emptyList())
        assertThat(manager.reconcile()).isTrue()

        assertThat(system.setting("target", "target.Wake")).isEqualTo(COMPONENT_ENABLED_STATE_DEFAULT)
        assertThat(system.changes).containsExactly("target/target.Wake 0 2->0")
        verify(ownership).save(claimed.copy(configured = false, pendingChange = false))
    }

    @Test fun givenSecondUser_whenWakeRetiresItsClaim_thenUsersBlockStays() = runTest {
        // With a second user no path qualifies (see ReviewedWakePaths), so wake retires its claims.
        system.put("target", "target.Wake", COMPONENT_ENABLED_STATE_DISABLED)
        userBlocks.userBlocked("target", "target.Wake")
        reconcileWith(previous = listOf(claimed), qualifying = emptyList())
        whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), listOf(0, 10)))

        assertThat(manager.reconcile()).isTrue()

        assertThat(system.setting("target", "target.Wake")).isEqualTo(COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(system.changes).isEmpty()
    }
}
