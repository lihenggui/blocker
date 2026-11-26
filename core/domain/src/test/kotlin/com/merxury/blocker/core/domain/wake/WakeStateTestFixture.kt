// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.wake.WakeReceiverResolver
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.database.wake.WakeFindingDay
import com.merxury.blocker.core.database.wake.WakeVerdictDay
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.wake.FakeAutomaticWakeBlockRepository
import com.merxury.blocker.core.testing.repository.wake.FakeWakeBlockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

internal const val TEST_APP = "example.app"
internal const val TEST_PEER = "example.peer"
internal const val TEST_OTHER = "example.other"

internal class WakeStateTestFixture(scope: TestScope) {
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val history = mock<WakeRepository>()
    val findings = MutableStateFlow<List<WakeFindingDay>>(emptyList())
    val verdicts = MutableStateFlow<List<WakeVerdictDay>>(emptyList())
    val manual = FakeWakeBlockRepository()
    val automatic = FakeAutomaticWakeBlockRepository()
    val components = FakeComponentStatusRepository()
    val receivers = mutableMapOf<Pair<String, String>, List<ComponentDeclaration>>()
    val observedApps = mutableListOf<AppKey>()
    private val statuses = mutableMapOf<UserId, MutableMap<ComponentId, ComponentStatus>>()
    private val tracked = object : ComponentStatusRepository by components {
        override fun components(app: AppKey) = components.components(app).also { observedApps += app }
    }

    init {
        whenever(history.findingsInvolving(any(), any())).thenAnswer { call ->
            val name = call.getArgument<String>(0)
            findings.map { list -> list.filter { it.target == name || it.actor == name } }
        }
        whenever(history.findingsSince(any())).thenReturn(findings)
        whenever(history.verdictsSince(any())).thenReturn(verdicts)
    }

    val activity = ObserveAppWakeActivityUseCase(history, dispatcher)
    private val observer = WakeStateObserver(
        manual,
        automatic,
        tracked,
        WakeReceiverResolver { target, action ->
            receivers[target to action].orEmpty()
        },
        dispatcher,
    )
    val state = ObserveAppWakeStateUseCase(activity, observer)
    val overview = ObserveWakeOverviewUseCase(history, observer, dispatcher)

    fun status(component: ComponentDeclaration, off: Boolean, user: UserId = UserId.SYSTEM) {
        val state = ControlState(if (off) PmSetting.DISABLED else PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
        val userStatuses = statuses.getOrPut(user, ::mutableMapOf)
        userStatuses[component.id] = ComponentStatus(component, state)
        components.sendComponents(user, userStatuses.values.toList())
    }
}

internal fun finding(
    key: String,
    trigger: ReviveTrigger = ReviveTrigger.PEER_SERVICE,
    target: String = TEST_APP,
    actor: String? = TEST_PEER,
    detail: String? = ".Wake",
    seen: Long = 100,
) = WakeFindingDay(
    key, LocalDate.now().toEpochDay(), target, key, trigger.name, null, actor, detail, true, null,
    "NONE", "DIRECT", null, 0, 1, 0, 0, seen * 1_000_000, 0,
)

internal fun verdict(target: String, verdict: WakeVerdict) = WakeVerdictDay(LocalDate.now().toEpochDay(), target, verdict.name, 1, 1, 0, 0, 0, 0, 0, 0)

internal fun WakePath.blockRow(at: Long = 100, method: WakeBlockMethod = blockMethod) = WakeBlock(pathKey, target, method, componentType, actor, component, action, at)

internal fun receiver(target: String, name: String) = ComponentDeclaration(ComponentId(target, "$target.$name"), ComponentType.RECEIVER, exported = true, enabledInManifest = true)
