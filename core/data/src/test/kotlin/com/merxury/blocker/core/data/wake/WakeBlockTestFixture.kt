// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DeviceRepositoryTestBase
import com.merxury.blocker.core.data.device.PmBlockClaimObserver
import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.data.device.installedApp
import com.merxury.blocker.core.data.history.OperationScope
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.ACTION
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.ACTIVITY
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.COMPONENT
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.FIREWALL
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.OTHER_ACTION
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.OTHER_RECEIVER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.OTHER_SERVICE
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.PEER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.PEER_UID
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.PROVIDER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.RECEIVER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.SERVICE
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.TARGET
import com.merxury.blocker.core.database.wake.WakeBlockDao
import com.merxury.blocker.core.database.wake.WakeBlockEntity
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.wake.FakeAutomaticWakeBlockRepository
import com.merxury.blocker.core.testing.util.TestWakeRunRequester
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeRuleWriter
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

internal object WakeBlockTestValues {
    const val TARGET = "target.pkg"
    const val PEER = "peer.pkg"
    const val PEER_UID = 12345
    const val SERVICE = "$TARGET.Service"
    const val OTHER_SERVICE = "$TARGET.OtherService"
    const val RECEIVER = "$TARGET.Receiver"
    const val OTHER_RECEIVER = "$TARGET.OtherReceiver"
    const val PROVIDER = "$TARGET.Provider"
    const val ACTIVITY = "$TARGET.Activity"
    const val ACTION = "sdk.WAKE"
    const val OTHER_ACTION = "sdk.OTHER_WAKE"
    val FIREWALL = WakeBlockMethod.FIREWALL_RULE
    val COMPONENT = WakeBlockMethod.COMPONENT
}

internal abstract class WakeBlockTestFixture {
    protected val context = RuntimeEnvironment.getApplication()
    protected val dao = FakeWakeBlockDao()
    protected val writer = FakeWakeRuleWriter()
    protected val components = FakeComponentStatusRepository()
    protected val receivers = FakeWakeReceiverResolver()
    protected val channel = FakePrivilegedChannel()
    protected val lock = WakeRuleLock()
    protected val automatic = FakeAutomaticWakeBlockRepository()
    protected val packages = mock<SystemPackageReader>()
    protected val firewall = UserWakeFirewall(dao, writer, packages)
    protected val rules = ManagedWakeRules(writer, context)

    protected suspend fun TestScope.repository(
        peerFilter: Boolean = true,
        privilegedChannel: PrivilegedChannel = channel,
        componentRepository: ComponentStatusRepository = components,
        claimRecords: PmBlockRecords? = null,
        protections: AutomaticWakeBlockRepository = automatic,
    ): WakeBlockRepository {
        installSender(PEER, PEER_UID)
        installSender("sender.pkg", PEER_UID + 1)
        val resolver = context.contentResolver
        Settings.Global.putInt(resolver, Settings.Global.BOOT_COUNT, 5)
        check(
            context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).edit()
                .putString("peer_probe_build", "${Build.FINGERPRINT}/5").putBoolean("peer_probe_ok", peerFilter).commit(),
        )
        components.sendComponents(
            UserId.SYSTEM,
            listOf(
                declaration(SERVICE, ComponentType.SERVICE),
                declaration(OTHER_SERVICE, ComponentType.SERVICE),
                declaration(RECEIVER, ComponentType.RECEIVER),
                declaration(OTHER_RECEIVER, ComponentType.RECEIVER),
                declaration(PROVIDER, ComponentType.PROVIDER),
                declaration(ACTIVITY, ComponentType.ACTIVITY),
            ).map { ComponentStatus(it, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)) },
        )
        val records = claimRecords ?: mock<PmBlockRecords>().also {
            whenever(it.recordsFor(AppKey(TARGET, UserId.SYSTEM))).thenReturn(emptyList())
        }
        return LocalWakeBlockRepository(dao, firewall, rules, componentRepository, receivers, privilegedChannel, lock, StandardTestDispatcher(testScheduler), protections, records, context)
    }

    protected fun installSender(sender: String, senderUid: Int) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(
            PackageInfo().apply {
                packageName = sender
                applicationInfo = ApplicationInfo().apply {
                    packageName = sender
                    uid = senderUid
                }
            },
        )
        pm.setPackagesForUid(senderUid, sender)
    }

    protected fun journaledComponents(records: PmBlockRecords) = object : ComponentStatusRepository by components {
        override fun control(request: ControlRequest, keepKnownState: Boolean, writeObserver: ComponentWriteObserver?, operation: OperationScope) = flow {
            val app = AppKey(TARGET, request.user)
            val toClaim = if (request.goal == ControlGoal.BLOCK) request.components.filter { pm(it) != PmSetting.DISABLED } else emptyList()
            val observer = writeObserver as? PmBlockClaimObserver
            observer?.onPmClaimsBefore(records.recordsFor(app).filter { row -> toClaim.any { it.id.className == row.className } })
            val added = records.recordNew(toClaim.map { it.key })
            components.control(request, keepKnownState, writeObserver, operation).collect { event ->
                if (event is ControlEvent.PackageDone && request.expectedPm == null) {
                    records.afterControl(event, forgetRefusedPmBlocks = false)
                    observer?.onPmClaimsAdded(added)
                }
                emit(event)
            }
        }
    }

    protected fun path(
        key: String,
        actor: String? = PEER,
        trigger: ReviveTrigger? = ReviveTrigger.PEER_SERVICE,
        hold: HoldCause? = null,
        component: String? = SERVICE,
        hint: ComponentType? = null,
        action: String? = null,
        method: WakeBlockMethod = FIREWALL,
    ) = WakePath(key, TARGET, actor, trigger, hold, component, hint, action, 1, 0, 0, 0, false, method)

    protected fun sendPm(vararg states: Pair<ComponentDeclaration, PmSetting>) {
        components.sendComponents(UserId.SYSTEM, states.map { (declaration, pm) -> ComponentStatus(declaration, ControlState(pm, IfwBlock.NOT_BLOCKED)) })
    }

    protected val ComponentDeclaration.key get() = ComponentKey(id, UserId.SYSTEM)

    protected suspend fun pm(declaration: ComponentDeclaration) = components.component(declaration.key).first()?.state?.pm

    protected fun actionPath(key: String) = path(key, trigger = ReviveTrigger.PEER_BROADCAST, component = null, action = ACTION, method = COMPONENT)

    protected fun declaration(name: String, type: ComponentType) = ComponentDeclaration(ComponentId(TARGET, name), type, exported = true, enabledInManifest = true)

    protected fun firewallRow(key: String, target: String) = WakeBlockEntity(key, target, FIREWALL.name, ComponentType.SERVICE.name, "sender.pkg", "$target.Service", null, 1)
}

internal class FakeWakeReceiverResolver : WakeReceiverResolver {
    val matches = mutableMapOf<Pair<String, String>, List<ComponentDeclaration>>()
    override fun resolve(target: String, action: String): List<ComponentDeclaration> = matches[target to action].orEmpty()
}

internal class FakeWakeBlockDao : WakeBlockDao {
    private val rows = MutableStateFlow<List<WakeBlockEntity>>(emptyList())
    override fun observeAll() = rows
    override suspend fun all() = rows.value

    override suspend fun upsert(blocks: List<WakeBlockEntity>) = update(blocks, emptyList())

    override suspend fun delete(pathKeys: List<String>) = update(emptyList(), pathKeys)

    override suspend fun deleteFirewall(target: String?) {
        rows.update { current -> current.filterNot { it.isFirewallBlock && (target == null || target == it.target) } }
    }

    override suspend fun update(added: List<WakeBlockEntity>, removed: List<String>) {
        val keys = added.mapTo(removed.toMutableSet()) { it.pathKey }
        rows.update { current -> (current.filterNot { it.pathKey in keys } + added).sortedBy { it.pathKey } }
    }
}

internal class FakeWakeRuleWriter : WakeRuleWriter {
    val userWrites = mutableListOf<String?>()
    val resets = mutableListOf<String?>()
    var failWrites = false
    var beforeWrite: suspend () -> Unit = {}

    override suspend fun write(kind: WakeRuleFileKind, xml: String?) {
        check(kind == WakeRuleFileKind.USER)
        beforeWrite()
        if (failWrites) throw IOException("test write failed")
        userWrites += xml
    }

    override suspend fun reset(packageName: String?) {
        resets += packageName
    }

    override suspend fun removeTarget(kind: WakeRuleFileKind, packageName: String) = error("Unexpected target removal")

    fun firewallRules() = IfwXmlDeserializer().deserialize(requireNotNull(userWrites.last())).rules
}
