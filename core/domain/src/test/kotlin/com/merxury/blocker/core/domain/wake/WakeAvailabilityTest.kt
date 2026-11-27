// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeSourceStatus
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.util.TestWakeRunRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WakeAvailabilityTest {
    private val ready = ChannelState.Ready(PrivilegedIdentity.SHELL)
    private val unavailable = ChannelState.Unavailable(UnavailableReason.DENIED)
    private val channel = FakePrivilegedChannel(ready)
    private val statuses = MutableStateFlow(emptyList<WakeSourceStatus>())
    private val findingsExist = MutableStateFlow(false)
    private val repository = mock<WakeRepository> {
        on { isOwnerProfile } doReturn true
        on { statuses() } doReturn this@WakeAvailabilityTest.statuses
        on { hasFindings() } doReturn findingsExist
    }
    private val observe = ObserveWakeAvailabilityUseCase(repository, WakeAuthorizationRepository(channel))
    private val runs = TestWakeRunRequester()
    private val request = RequestWakeCheckUseCase(observe, runs)
    private val waiting = WakeSourceStatus("authorization", 10L, "WAITING_FOR_PERMISSION")
    private val refused = WakeSourceStatus("protection", 20L, "OBSERVING_NOTHING_READS_REFUSED")
    private val detectionOk = WakeSourceStatus("detection", 30L, "OK")
    private val detectionFailure = WakeSourceStatus("detection", 50L, "evidence unreadable")

    @Test
    fun givenSecondaryProfile_whenAccessChanges_thenOwnerProfileRequirementTakesPrecedence() = runTest {
        whenever(repository.isOwnerProfile).thenReturn(false)
        statuses.value = listOf(waiting, refused, detectionOk)
        channel.setState(unavailable)

        observe().test {
            assertThat(awaitItem()).isEqualTo(WakeAvailability.NotOwnerProfile)
            awaitComplete()
        }
        channel.setState(ready)
        observe().test {
            assertThat(awaitItem()).isEqualTo(WakeAvailability.NotOwnerProfile)
            awaitComplete()
        }
        verify(repository, never()).statuses()
    }

    @Test
    fun givenSavedRefusalAndResults_whenAccessIsGrantedAndLost_thenLiveAccessTakesPrecedence() = runTest {
        statuses.value = listOf(waiting, refused, detectionOk)
        channel.setState(unavailable)

        observe().test {
            assertThat(awaitItem()).isEqualTo(WakeAvailability.NoAccess)
            verify(repository, never()).statuses()

            channel.setState(ready)
            assertThat(awaitItem()).isEqualTo(WakeAvailability.ReadsRefused)

            statuses.value = listOf(waiting, refused.copy(status = "OBSERVING_WITH_SHELL"), detectionOk)
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(30L))

            channel.setState(unavailable)
            assertThat(awaitItem()).isEqualTo(WakeAvailability.NoAccess)

            channel.setState(ready)
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(30L))
        }
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenNoFindings_whenDetectionSucceeds_thenAvailabilityIsReadyWithTheCheckTime() = runTest {
        observe().test {
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Collecting)

            statuses.value = listOf(detectionOk)
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(30L))

            statuses.value = listOf(detectionOk.copy(checkedAt = 40L))
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(40L))
        }
    }

    @Test
    fun givenStoredFindings_whenDetectionFails_thenAvailabilityStaysReadyWithoutACheckTime() = runTest {
        findingsExist.value = true
        statuses.value = listOf(detectionOk)

        observe().test {
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(30L))

            statuses.value = listOf(detectionFailure)
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(null))
        }
    }

    @Test
    fun givenNoFindings_whenDetectionFails_thenAvailabilityIsCollecting() = runTest {
        val protection = refused.copy(checkedAt = 25L, status = "OK")
        statuses.value = listOf(protection, detectionFailure)

        observe().test {
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Collecting)

            findingsExist.value = true
            assertThat(awaitItem()).isEqualTo(WakeAvailability.Ready(null))
        }
    }

    @Test
    fun givenCollectingOrReady_whenRequestingCheck_thenRunIsRequested() = runTest {
        assertThat(request()).isTrue()
        assertThat(runs.requests).isEqualTo(1)

        statuses.value = listOf(detectionOk)
        assertThat(request()).isTrue()
        assertThat(runs.requests).isEqualTo(2)
    }

    @Test
    fun givenUnavailableOrRefusedChannelOrSecondaryProfile_whenRequestingCheck_thenNoRunIsRequested() = runTest {
        statuses.value = listOf(refused, detectionOk)
        channel.setState(unavailable)
        assertThat(request()).isFalse()

        channel.setState(ready)
        assertThat(request()).isFalse()

        whenever(repository.isOwnerProfile).thenReturn(false)
        statuses.value = listOf(detectionOk)
        assertThat(request()).isFalse()
        assertThat(runs.requests).isEqualTo(0)
        assertThat(channel.connectCount).isEqualTo(0)
    }
}
