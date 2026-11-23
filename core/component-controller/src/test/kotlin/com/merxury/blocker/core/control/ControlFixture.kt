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

package com.merxury.blocker.core.control

import android.content.pm.PackageManager
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.di.IfwModule
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal const val PKG = "com.example"
internal const val OTHER_PKG = "com.example.other"
internal val OWNER = UserId(0)
internal val WORK = UserId(10)

internal val PM_ONLY = setOf(ControlLayer.PACKAGE_MANAGER)
internal val IFW_ONLY = setOf(ControlLayer.INTENT_FIREWALL)
internal val BOTH = setOf(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)

internal fun declaration(
    simpleName: String,
    type: ComponentType = ComponentType.ACTIVITY,
    packageName: String = PKG,
    enabledInManifest: Boolean = true,
) = ComponentDeclaration(ComponentId(packageName, "$packageName.$simpleName"), type, exported = true, enabledInManifest = enabledInManifest)

internal fun block(layers: Set<ControlLayer>, vararg components: ComponentDeclaration, user: UserId = OWNER) = ControlRequest(user, components.toList(), ControlGoal.BLOCK, layers)

internal fun unblock(vararg components: ComponentDeclaration, layers: Set<ControlLayer> = BOTH, user: UserId = OWNER) = ControlRequest(user, components.toList(), ControlGoal.UNBLOCK, layers)

internal fun List<ControlEvent>.outcomes(): List<ComponentOutcome> = filterIsInstance<ControlEvent.PackageDone>().flatMap { it.outcomes }

internal fun List<ControlEvent>.outcomeOf(component: ComponentDeclaration, user: UserId = OWNER): ComponentOutcome = outcomes().single { it.key.id == component.id && it.key.userId == user }

/** The control under test over fakes that share one [system] and record what reaches them. */
internal class ControlFixture(initialChannel: ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT)) {
    val system = FakePmSystem()
    val channel = FakeChannel(initialChannel)
    val executor = FakeExecutor(system, channel)
    val files = FakeIfwFileSystem()
    val store = IfwModule.providesIfwStore(files, IfwXmlSerializer(), IfwXmlDeserializer())
    val control = PrivilegedComponentControl(channel, executor, FakeReader(system), store)

    suspend fun run(request: ControlRequest): List<ControlEvent> = control.apply(request).toList()

    fun pm(component: ComponentDeclaration, user: UserId = OWNER): PmSetting = system.setting(user, component.id)

    /** Writes rules as an earlier Blocker would, then forgets the operations it recorded. */
    suspend fun blockInIfw(vararg components: ComponentDeclaration) {
        store.setBlocked(components.first().id.packageName, components.associateWith { true })
        files.operations.clear()
    }

    /** IFW file operations that changed something. */
    val ifwWrites: List<String> get() = files.operations.filter { it.startsWith("write") || it.startsWith("delete") }
}

/**
 * PackageManager settings per user. An absent entry is [PmSetting.DEFAULT], which is also how
 * Android stores DEFAULT: as no override at all.
 */
internal class FakePmSystem {
    val settings = ConcurrentHashMap<Pair<UserId, ComponentId>, PmSetting>()

    /** Reads as `read <package>`, calls as `set <package> <count> <setting>`, in order. */
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Components the system refuses to change, as it does for some of protected system packages. */
    val protected: MutableSet<ComponentId> = ConcurrentHashMap.newKeySet()

    /** Packages not installed: reads fail and changes are rejected. */
    val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** When true, calls succeed but change nothing. */
    @Volatile
    var ignoreWrites = false

    /** Every call fails with this when set. */
    @Volatile
    var callFailure: CallFailure? = null

    /** Every read fails with this when set. */
    @Volatile
    var readFailure: ReadFailure? = null

    /** Runs before each call is applied, so tests can hold it or change the world. */
    @Volatile
    var beforeSet: suspend (SetComponentEnabledSettings) -> Unit = {}

    fun setting(user: UserId, id: ComponentId): PmSetting = settings[user to id] ?: PmSetting.DEFAULT

    fun put(user: UserId, id: ComponentId, setting: PmSetting) {
        if (setting == PmSetting.DEFAULT) settings.remove(user to id) else settings[user to id] = setting
    }
}

internal class FakeChannel(initial: ChannelState) : PrivilegedChannel {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ChannelState> = mutableState

    /** What [connect] gives. */
    var connectResult: ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT)
    var connectCount = 0
        private set

    override suspend fun connect(): ChannelState {
        connectCount++
        mutableState.value = connectResult
        return connectResult
    }

    override suspend fun connectIfGranted(): ChannelState = state.value
}

/**
 * Runs [SetComponentEnabledSettings] against [system] as Android 13 and later do: all or none.
 * The shell is refused as Android refuses it.
 */
internal class FakeExecutor(private val system: FakePmSystem, private val channel: FakeChannel) : PrivilegedExecutor {
    val calls: MutableList<SetComponentEnabledSettings> = Collections.synchronizedList(mutableListOf())

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> {
        if (channel.state.value !is ChannelState.Ready) channel.connect()
        return executeIfReady(call)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> {
        val ready = channel.state.value as? ChannelState.Ready ?: return CallResult.Failure(CallFailure.ChannelUnavailable)
        val set = call as? SetComponentEnabledSettings ?: error("Unexpected call $call")
        calls += set
        val ids = set.components.map { ComponentId(it.packageName, it.className) }
        val setting = set.newState.toPmSetting()
        system.log += "set ${ids.first().packageName} ${ids.size} $setting"
        system.beforeSet(set)
        val failure = when {
            system.callFailure != null -> system.callFailure
            !ready.identity.canChangeComponents -> CallFailure.NotPermitted("Shell cannot change components")
            ids.any { it.packageName in system.missing } -> CallFailure.TargetNotFound("Unknown package")
            ids.any { it in system.protected } -> CallFailure.NotPermitted("Protected component")
            else -> null
        }
        if (failure != null) return CallResult.Failure(failure)
        if (!system.ignoreWrites) ids.forEach { system.put(set.user, it, setting) }
        return CallResult.Success(Unit) as CallResult<R>
    }

    private fun Int.toPmSetting() = when (this) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> PmSetting.DEFAULT
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> PmSetting.ENABLED
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> PmSetting.DISABLED
        else -> error("Not a component setting: $this")
    }
}

internal class FakeReader(private val system: FakePmSystem) : SystemPackageReader {
    override suspend fun pmSettings(app: AppKey, components: Collection<ComponentId>): ReadResult<Map<ComponentId, PmSetting>> {
        system.log += "read ${app.packageName}"
        system.readFailure?.let { return ReadResult.Failure(it) }
        if (app.packageName in system.missing) return ReadResult.Failure(ReadFailure.PackageNotFound(app))
        return ReadResult.Success(
            components.associateWith { if (it.packageName == app.packageName) system.setting(app.userId, it) else PmSetting.UNKNOWN },
        )
    }

    override suspend fun users(): ReadResult<List<DeviceUser>> = error("Not used")

    override suspend fun apps(user: UserId): ReadResult<List<InstalledApp>> = error("Not used")

    override suspend fun app(key: AppKey): ReadResult<InstalledApp> = error("Not used")

    override suspend fun declarations(app: AppKey): ReadResult<List<ComponentDeclaration>> = error("Not used")
}
