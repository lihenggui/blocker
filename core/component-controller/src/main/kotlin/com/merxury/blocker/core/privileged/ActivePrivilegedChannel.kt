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

package com.merxury.blocker.core.privileged

import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.privileged.shizuku.ShizukuTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The channel for the controller type the user chose: the root transport for the types that work
 * through root, the Shizuku transport for Shizuku. It never falls back from one to the other.
 * When the choice changes, [state] follows the newly chosen transport.
 */
@Singleton
internal class ActivePrivilegedChannel(
    controllerTypes: Flow<ControllerType>,
    private val root: PrivilegedTransport,
    private val shizuku: PrivilegedTransport,
    scope: CoroutineScope,
) : PrivilegedChannel,
    PrivilegedExecutor {

    @Inject constructor(
        preferences: BlockerPreferencesDataSource,
        root: RootTransport,
        shizuku: ShizukuTransport,
        @ApplicationScope scope: CoroutineScope,
    ) : this(preferences.userData.map { it.controllerType }, root, shizuku, scope)

    private val transports: Flow<PrivilegedTransport> = controllerTypes.map(::transportFor).distinctUntilChanged()

    private val selected: StateFlow<PrivilegedTransport?> = transports
        .catch { Timber.e(it, "Cannot read the controller type") }
        .stateIn(scope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: StateFlow<ChannelState> = selected
        .flatMapLatest { it?.state ?: flowOf(NOT_CHECKED) }
        .stateIn(scope, SharingStarted.Eagerly, NOT_CHECKED)

    override suspend fun connect(): ChannelState = current()?.connect() ?: ChannelState.Unavailable(UnavailableReason.FAILED)

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> = current()?.execute(call) ?: CallResult.Failure(CallFailure.ChannelUnavailable)

    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> = selected.value?.executeIfReady(call) ?: CallResult.Failure(CallFailure.ChannelUnavailable)

    /** The chosen transport, waiting for the first read of the choice. Null when it cannot be read. */
    private suspend fun current(): PrivilegedTransport? = selected.value ?: try {
        transports.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Cannot read the controller type")
        null
    }

    private fun transportFor(type: ControllerType): PrivilegedTransport = when (type) {
        ControllerType.PM, ControllerType.IFW, ControllerType.IFW_PLUS_PM -> root
        ControllerType.SHIZUKU -> shizuku
    }

    private companion object {
        val NOT_CHECKED = ChannelState.Unavailable(UnavailableReason.NOT_CHECKED)
    }
}
