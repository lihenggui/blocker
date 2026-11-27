// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.device

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_ERRORED
import android.app.AppOpsManager.MODE_IGNORED
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.appcontrol.AppRestrictionControl
import com.merxury.blocker.core.appcontrol.AppRestrictionReading
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppRestrictionRepositoryTest {
    private val app = AppKey("com.example.restricted", UserId(10))
    private val otherUser = app.copy(userId = UserId.SYSTEM)
    private val defaults = DeepRestrictionSnapshot(10, MODE_ALLOWED, MODE_DEFAULT)
    private val restriction = DeepRestrictionSnapshot(45, MODE_IGNORED, MODE_IGNORED)
    private val baseline = DeepRestrictionSnapshot(30, MODE_ERRORED, MODE_ALLOWED)
    private val ready = ChannelState.Ready(PrivilegedIdentity.SHELL)
    private val channel = FakePrivilegedChannel(ready).apply { connectResult = ready }
    private val control = FakeAppRestrictionControl()

    private fun TestScope.repository() = LocalAppRestrictionRepository(channel, control, RuntimeEnvironment.getApplication(), StandardTestDispatcher(testScheduler))

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenTwoObservers_whenWriting_thenOnlyTheirAppChanges() = runTest {
        val repository = repository()
        repository.observe(app).test firstObserver@{
            assertThat(awaitItem()).isEqualTo(defaults.setting())
            repository.observe(app).test {
                assertThat(awaitItem()).isEqualTo(defaults.setting())
                runCurrent()
                assertThat(channel.quietConnectCount).isEqualTo(2)
                assertThat(channel.connectCount).isEqualTo(0)

                repository.observe(otherUser).test {
                    assertThat(awaitItem()).isEqualTo(defaults.setting())
                    assertThat(repository.setDeepRestricted(app, true)).isEqualTo(AppRestrictionResult.Success(restriction.setting()))
                    expectNoEvents()
                }
                assertThat(awaitItem()).isEqualTo(restriction.setting())
                assertThat(this@firstObserver.awaitItem()).isEqualTo(restriction.setting())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenUncachedRestrictedApp_whenObserving_thenWaitsForTheFirstRead() = runTest {
        val repository = repository()
        control.states[app] = restriction
        val firstRead = CompletableDeferred<Unit>()
        control.beforeRead = { firstRead.await() }

        repository.observe(app).test {
            runCurrent()
            assertThat(control.reads).containsExactly(app)
            expectNoEvents()

            firstRead.complete(Unit)
            assertThat(awaitItem()).isEqualTo(restriction.setting())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenCachedSettings_whenResubscribingAndReconnecting_thenObserversSeeFreshValuesWithoutPrompting() = runTest {
        val repository = repository()
        assertThat(repository.read(app)).isEqualTo(AppRestrictionResult.Success(defaults.setting()))
        control.states[app] = restriction
        val refresh = CompletableDeferred<Unit>()
        control.beforeRead = { refresh.await() }
        repository.observe(app).test {
            assertThat(awaitItem()).isEqualTo(defaults.setting())
            runCurrent()
            assertThat(control.reads).containsExactly(app, app)
            refresh.complete(Unit)
            assertThat(awaitItem()).isEqualTo(restriction.setting())
            channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
            runCurrent()
            control.states[app] = defaults
            channel.setState(ready)
            assertThat(awaitItem()).isEqualTo(defaults.setting())
            assertThat(channel.quietConnectCount).isEqualTo(3)
            assertThat(channel.connectCount).isEqualTo(0)
        }
    }

    @Test fun givenRecentlyAllowlistedApp_whenWriting_thenRefusalAndUpdatedProtectedStateAreReported() = runTest {
        val repository = repository()
        repository.observe(app).test {
            assertThat(awaitItem()?.changeable).isTrue()
            control.protected = true

            assertThat(repository.setDeepRestricted(app, true)).isEqualTo(AppRestrictionResult.Failure(AppRestrictionFailure.NOT_CHANGEABLE))
            assertThat(awaitItem()?.changeable).isFalse()
            assertThat(control.states).isEmpty()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenWriteInProgress_whenReading_thenReadWaitsForTheWrite() = runTest {
        val repository = repository()
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        control.beforeWrite = {
            started.complete(Unit)
            complete.await()
        }

        val write = async { repository.setDeepRestricted(app, true) }
        started.await()
        val read = async { repository.read(app) }
        runCurrent()
        assertThat(control.reads).containsExactly(app)
        complete.complete(Unit)

        assertThat(write.await()).isEqualTo(AppRestrictionResult.Success(restriction.setting()))
        assertThat(read.await()).isEqualTo(AppRestrictionResult.Success(restriction.setting()))
        assertThat(control.reads).containsExactly(app, app, app)
    }

    @Test fun givenFailedWriteAndMissingApp_whenReadingBack_thenCacheClearsAndWriteFailureSurfaces() = runTest {
        val repository = repository()
        repository.observe(app).test {
            assertThat(awaitItem()).isEqualTo(defaults.setting())
            control.writeFailure = CallFailure.SystemError("AppOps write failed")
            control.beforeWrite = { control.readFailure = CallFailure.TargetNotFound(null) }

            assertThat(repository.setDeepRestricted(app, true)).isEqualTo(AppRestrictionResult.Failure(AppRestrictionFailure.SYSTEM_ERROR))
            assertThat(awaitItem()).isNull()
            assertThat(repository.read(app)).isEqualTo(AppRestrictionResult.Failure(AppRestrictionFailure.NOT_INSTALLED))
        }
    }

    @Test fun givenSystemRestrictionWithNoJournal_whenRestoring_thenNoSystemWriteOrPromptOccurs() = runTest {
        val repository = repository()
        control.states[app] = restriction

        assertThat(repository.restoreOwned(app)).isEqualTo(AppRestrictionRestoreResult.NotOwned)
        assertThat(control.states[app]).isEqualTo(restriction)
        assertThat(control.reads).isEmpty()
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test fun givenRepeatedEnablesAndAnExternalChange_whenRestoringAndUndoing_thenExactValuesAreUsed() = runTest {
        val repository = repository()
        control.states[app] = baseline
        repository.setDeepRestricted(app, true)
        repository.setDeepRestricted(app, true)
        val beforeRestore = restriction.copy(standbyBucket = 20)
        control.states[app] = beforeRestore

        val reopened = repository()
        val restored = baseline.copy(standbyBucket = 20)
        val undo = AppRestrictionRestoreResult.Restored(beforeRestore, restored, control.installTime)
        assertThat(reopened.restoreOwned(app)).isEqualTo(undo)
        assertThat(control.states[app]).isEqualTo(restored)
        assertThat(reopened.restoreOwned(app)).isEqualTo(AppRestrictionRestoreResult.NotOwned)

        assertThat(reopened.reapplyOwned(app, undo.reapply, undo.baseline, undo.firstInstallTime))
            .isEqualTo(AppRestrictionResult.Success(beforeRestore.setting()))
        assertThat(control.states[app]).isEqualTo(beforeRestore)
        assertThat(reopened.restoreOwned(app)).isEqualTo(undo)
        assertThat(control.states[app]).isEqualTo(restored)
    }

    @Test fun givenOneFieldChangedAfterEnable_whenReenablingAndRestoring_thenOnlyThatFieldGetsANewBaseline() = runTest {
        val repository = repository()
        assertThat(repository.setDeepRestricted(app, true)).isEqualTo(AppRestrictionResult.Success(restriction.setting()))
        control.states[app] = restriction.copy(standbyBucket = 20)

        assertThat(repository.setDeepRestricted(app, true)).isEqualTo(AppRestrictionResult.Success(restriction.setting()))
        assertThat(control.states[app]).isEqualTo(restriction)
        assertThat(repository.restoreOwned(app)).isInstanceOf(AppRestrictionRestoreResult.Restored::class.java)
        assertThat(control.states[app]).isEqualTo(defaults.copy(standbyBucket = 20))
    }

    @Test fun givenOneFieldChangedAfterRestore_whenUndoing_thenOnlyUntouchedFieldsAreReapplied() = runTest {
        val repository = repository()
        repository.setDeepRestricted(app, true)
        val restored = repository.restoreOwned(app) as AppRestrictionRestoreResult.Restored
        assertThat(control.states[app]).isEqualTo(defaults)
        control.states[app] = defaults.copy(standbyBucket = 30)

        val expected = restriction.copy(standbyBucket = 30)
        assertThat(repository.reapplyOwned(app, restored.reapply, restored.baseline, restored.firstInstallTime))
            .isEqualTo(AppRestrictionResult.Success(expected.setting()))
        assertThat(control.states[app]).isEqualTo(expected)
        assertThat(repository.restoreOwned(app)).isInstanceOf(AppRestrictionRestoreResult.Restored::class.java)
        assertThat(control.states[app]).isEqualTo(defaults.copy(standbyBucket = 30))
    }

    @Test fun givenLostReplyAfterAPartialWrite_whenRestoring_thenOnlyConfirmedWritesAreOwned() = runTest {
        val repository = repository()
        val partial = restriction.copy(standbyBucket = 20, exactAlarmMode = MODE_DEFAULT)
        control.afterWrite = {
            control.states[app] = partial
            CallFailure.ChannelUnavailable
        }

        assertThat(repository.setDeepRestricted(app, true)).isEqualTo(AppRestrictionResult.Failure(AppRestrictionFailure.NO_ACCESS))
        assertThat(control.states[app]).isEqualTo(partial)
        control.afterWrite = { null }
        // The bucket reaches the requested value later, independently of the failed write.
        control.states[app] = partial.copy(standbyBucket = 45)

        assertThat(repository().restoreOwned(app)).isInstanceOf(AppRestrictionRestoreResult.Restored::class.java)
        assertThat(control.states[app]).isEqualTo(defaults.copy(standbyBucket = 45))
    }

    @Test fun givenLostUndoReplyAndUnavailableReadBack_whenRestoringLater_thenTheWriteAheadEntryRecoversTheBaseline() = runTest {
        val repository = repository()
        repository.setDeepRestricted(app, true)
        val restored = repository.restoreOwned(app) as AppRestrictionRestoreResult.Restored
        control.afterWrite = {
            control.readFailure = CallFailure.ChannelUnavailable
            CallFailure.ChannelUnavailable
        }

        assertThat(repository.reapplyOwned(app, restored.reapply, restored.baseline, restored.firstInstallTime))
            .isEqualTo(AppRestrictionResult.Failure(AppRestrictionFailure.NO_ACCESS))
        assertThat(control.states[app]).isEqualTo(restriction)
        control.afterWrite = { null }
        control.readFailure = null

        assertThat(repository().restoreOwned(app)).isEqualTo(restored)
        assertThat(control.states[app]).isEqualTo(defaults)
    }

    @Test fun givenAnAppWasReinstalled_whenRestoringOrUndoing_thenTheNewInstallationIsUntouched() = runTest {
        val repository = repository()
        control.states[app] = baseline
        repository.setDeepRestricted(app, true)
        val firstInstall = control.installTime
        control.installTime++

        assertThat(repository.restoreOwned(app)).isEqualTo(AppRestrictionRestoreResult.NotOwned)
        assertThat(repository.reapplyOwned(app, baseline, defaults, firstInstall))
            .isEqualTo(AppRestrictionResult.Failure(AppRestrictionFailure.NOT_INSTALLED))
        assertThat(control.states[app]).isEqualTo(restriction)
    }

    @Test fun givenOwnedOrUnownedPartialRestrictions_whenManuallyDisabling_thenTheyAreCleared() = runTest {
        val repository = repository()
        control.states[app] = restriction.copy(runInBackgroundMode = MODE_ALLOWED)
        assertThat(repository.setDeepRestricted(app, false)).isEqualTo(AppRestrictionResult.Success(defaults.setting()))
        assertThat(control.states[app]).isEqualTo(defaults)

        val original = baseline.copy(runInBackgroundMode = MODE_IGNORED)
        control.states[app] = original
        repository.setDeepRestricted(app, true)
        assertThat(repository.setDeepRestricted(app, false)).isEqualTo(AppRestrictionResult.Success(defaults.setting()))
        assertThat(control.states[app]).isEqualTo(baseline.copy(runInBackgroundMode = MODE_ALLOWED))
        assertThat(repository.restoreOwned(app)).isEqualTo(AppRestrictionRestoreResult.NotOwned)
    }

    private fun DeepRestrictionSnapshot.setting(changeable: Boolean = true) = DeepBackgroundRestriction(
        standbyBucket == 45,
        runInBackgroundMode == MODE_IGNORED,
        exactAlarmMode == MODE_IGNORED,
        changeable,
    )

    private inner class FakeAppRestrictionControl : AppRestrictionControl {
        val states = mutableMapOf<AppKey, DeepRestrictionSnapshot>()
        val reads = mutableListOf<AppKey>()
        var writeFailure: CallFailure? = null
        var readFailure: CallFailure? = null
        var beforeRead: suspend () -> Unit = {}
        var beforeWrite: suspend () -> Unit = {}
        var afterWrite: () -> CallFailure? = { null }
        var protected = false
        var installTime = 100L

        override suspend fun read(app: AppKey): CallResult<AppRestrictionReading> {
            reads += app
            beforeRead()
            return readFailure?.let { CallResult.Failure(it) } ?: CallResult.Success(reading(app))
        }

        override suspend fun setDeepRestricted(
            app: AppKey,
            before: AppRestrictionReading,
            restricted: Boolean,
            target: DeepRestrictionSnapshot?,
        ): CallResult<AppRestrictionReading> {
            beforeWrite()
            writeFailure?.let { return CallResult.Failure(it) }
            check(before == reading(app))
            if (!protected) states[app] = target ?: if (restricted) restriction else defaults
            return afterWrite()?.let { CallResult.Failure(it) } ?: CallResult.Success(reading(app))
        }

        private fun reading(app: AppKey): AppRestrictionReading {
            val state = states[app] ?: defaults
            return AppRestrictionReading(state.setting(changeable = !protected), state, installTime)
        }
    }
}
