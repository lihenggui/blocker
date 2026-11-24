// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.test.repository.FakeUserDataRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferencesSerializer
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlLayer.PACKAGE_MANAGER
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
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

/** Exercises LibChecker's calls through the provider and the Hilt entry point. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ComponentProviderContractTest.TestApp::class)
class ComponentProviderContractTest {

    interface TestEntryPoint :
        ComponentProvider.ProviderEntryPoint,
        GeneratedComponent

    class TestApp :
        Application(),
        GeneratedComponentManager<TestEntryPoint> {
        lateinit var entryPoint: TestEntryPoint

        var entryPointReads = 0
            private set

        override fun generatedComponent(): TestEntryPoint {
            entryPointReads++
            return entryPoint
        }
    }

    private val repository = FakeComponentStatusRepository()
    private val analytics = TestAnalyticsHelper()
    private val channel = FakePrivilegedChannel()
    private val selectedUsers = FakeSelectedUserRepository(OWN_USER)
    private val userData = FakeUserDataRepository(
        BlockerPreferencesDataSource(InMemoryDataStore(UserPreferencesSerializer().defaultValue)),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var application: TestApp
    private lateinit var provider: ComponentProvider
    private var refresh: suspend (AppKey) -> ReadResult<Unit> = repository::refresh

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext<TestApp>()
        application.entryPoint = object : TestEntryPoint {
            override fun componentStatuses(): ComponentStatusRepository = object : ComponentStatusRepository by repository {
                override suspend fun refresh(app: AppKey): ReadResult<Unit> = this@ComponentProviderContractTest.refresh(app)
            }

            override fun selectedUsers(): SelectedUserRepository = selectedUsers
            override fun controlLayers() = GetControlLayersUseCase(userData)
            override fun privilegedChannel(): PrivilegedChannel = channel
            override fun analyticsHelper(): AnalyticsHelper = analytics
            override fun applicationScope(): CoroutineScope = scope
            override fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
        }
        provider = Robolectric.buildContentProvider(ComponentProvider::class.java)
            .create("com.merxury.blocker.provider.test")
            .get()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun givenBlockedComponents_whenGetComponents_thenReturnsExactJsonForBothReasons() = runTest {
        seed(
            status("PmOff", pm = PmSetting.DISABLED),
            status("IfwOff", ifw = IfwBlock.BLOCKED),
            status("BothOff", pm = PmSetting.DISABLED, ifw = IfwBlock.BLOCKED),
            status("Enabled"),
        )

        assertComponents(
            """{"pkg":"com.example.app","components":[""" +
                """{"type":"com.example.app","name":"com.example.app.BothOff","block":true},""" +
                """{"type":"com.example.app","name":"com.example.app.IfwOff","block":true},""" +
                """{"type":"com.example.app","name":"com.example.app.PmOff","block":true}]}""",
        )
        assertThat(repository.refreshedApps).containsExactly(APP)
    }

    @Test
    fun givenShippedDisabledAndConditionalRule_whenGetComponents_thenReturnsEmptyJsonArray() = runTest {
        seed(status("ManifestOff", enabledInManifest = false), status("Conditional", ifw = IfwBlock.CONDITIONAL))

        assertComponents(EMPTY_JSON)
    }

    @Test
    fun givenUninstalledPackageWithCachedRows_whenGetComponents_thenReturnsEmptyJsonArray() = runTest {
        seed(status("Tracker", pm = PmSetting.DISABLED))
        repository.refreshFailure = ReadFailure.PackageNotFound(APP)

        assertComponents(EMPTY_JSON)
        assertThat(repository.refreshedApps).containsExactly(APP)
    }

    @Test
    fun givenAccessMissingAndCachedBlocks_whenGetComponents_thenReturnsCachedJson() = runTest {
        channel.setState(DENIED)
        seed(status("Tracker", pm = PmSetting.DISABLED, ifw = IfwBlock.UNKNOWN))
        refresh = { app ->
            seed(status("Tracker", pm = PmSetting.UNKNOWN, ifw = IfwBlock.UNKNOWN))
            repository.refresh(app)
        }

        assertComponents(TRACKER_JSON)
        assertThat(repository.refreshedApps).containsExactly(APP)
        assertQuietConnect()
    }

    @Test
    fun givenAccessMissingAndNoCachedComponents_whenGetComponents_thenReturnsEmptyJsonArray() = runTest {
        channel.setState(DENIED)
        repository.refreshFailure = ReadFailure.ChannelUnavailable

        assertComponents(EMPTY_JSON)
        assertQuietConnect()
    }

    @Test
    fun givenRefreshFails_whenGetComponents_thenReturnsCachedJson() = runTest {
        seed(status("Tracker", pm = PmSetting.DISABLED))
        refresh = { error("Component refresh failed") }

        assertComponents(TRACKER_JSON)
    }

    @Test
    fun givenUnreadableRules_whenGetComponents_thenOnlyIncludesPmBlocks() = runTest {
        seed(status("Tracker", pm = PmSetting.DISABLED), status("Filter", ifw = IfwBlock.BLOCKED))
        repository.refreshFailure = ReadFailure.RulesUnreadable(PACKAGE_NAME)
        refresh = { app ->
            seed(status("Tracker", pm = PmSetting.DISABLED), status("Filter", ifw = IfwBlock.UNKNOWN))
            repository.refresh(app)
        }

        assertComponents(TRACKER_JSON)
    }

    @Test
    fun givenAnotherUserSelected_whenGetComponents_thenRefreshesAndReportsBlockersOwnUser() = runTest {
        selectedUsers.sendUsers(
            listOf(
                DeviceUser(OWN_USER, null, UserKind.SECONDARY, null, running = true),
                DeviceUser(UserId.SYSTEM, null, UserKind.OWNER, null, running = true),
            ),
        )
        selectedUsers.select(UserId.SYSTEM)
        repository.sendComponents(UserId.SYSTEM, listOf(status("Different", pm = PmSetting.DISABLED)))
        refresh = { app ->
            seed(status("Tracker", ifw = IfwBlock.BLOCKED))
            repository.refresh(app)
        }

        assertComponents(TRACKER_JSON)
        assertThat(repository.refreshedApps).containsExactly(APP)
    }

    @Test
    fun givenTwoOffs_whenBlocks_thenUsesOneRequestWithSettingsLayersForEachController() = runTest {
        val settingsLayers = mapOf(
            ControllerType.PM to setOf(PACKAGE_MANAGER),
            ControllerType.IFW to setOf(INTENT_FIREWALL),
            ControllerType.IFW_PLUS_PM to setOf(PACKAGE_MANAGER, INTENT_FIREWALL),
            ControllerType.SHIZUKU to setOf(PACKAGE_MANAGER),
        )
        val service = status("A")
        val contentProvider = status("B", type = ComponentType.PROVIDER)
        val input = input("A" to true, "B" to true)

        for ((type, layers) in settingsLayers) {
            userData.setControllerType(type)
            seed(service, contentProvider)
            repository.requests.clear()

            assertThat(call("blocks", PACKAGE_NAME, input)).isSameInstanceAs(input)

            assertThat(repository.requests).containsExactly(
                ControlRequest(OWN_USER, listOf(service.declaration, contentProvider.declaration), ControlGoal.BLOCK, layers),
            )
            assertThat(repository.component(ComponentKey(contentProvider.declaration.id, OWN_USER)).first()?.state?.pm)
                .isEqualTo(PmSetting.DISABLED)
        }
    }

    @Test
    fun givenTwoOns_whenBlocks_thenUsesOneEnableRequestWithEveryLayerForEachController() = runTest {
        val service = status("A", pm = PmSetting.DISABLED, ifw = IfwBlock.BLOCKED)
        val receiver = status("B", type = ComponentType.RECEIVER, enabledInManifest = false)
        val input = input("A" to false, "B" to false)

        for (type in ControllerType.entries) {
            userData.setControllerType(type)
            seed(service, receiver)
            repository.requests.clear()

            assertThat(call("blocks", PACKAGE_NAME, input)).isSameInstanceAs(input)

            assertThat(repository.requests).containsExactly(
                ControlRequest(OWN_USER, listOf(service.declaration, receiver.declaration), ControlGoal.ENABLE, ControlLayer.entries.toSet()),
            )
            assertThat(repository.component(ComponentKey(receiver.declaration.id, OWN_USER)).first()?.state?.pm)
                .isEqualTo(PmSetting.ENABLED)
        }
    }

    @Test
    fun givenMixedEntriesAndDifferentJsonPackage_whenBlocks_thenUsesTheArgumentAndLogsEachEntry() = runTest {
        val a = status("A")
        val b = status("B", ifw = IfwBlock.BLOCKED)
        val c = status("C")
        seed(a, b, c)
        userData.setControllerType(ControllerType.IFW)
        val input = input("B" to false, "A" to true, "C" to true)

        assertThat(call("blocks", PACKAGE_NAME, input)).isSameInstanceAs(input)

        assertThat(repository.requests).containsExactly(
            ControlRequest(OWN_USER, listOf(a.declaration, c.declaration), ControlGoal.BLOCK, setOf(INTENT_FIREWALL)),
            ControlRequest(OWN_USER, listOf(b.declaration), ControlGoal.ENABLE, ControlLayer.entries.toSet()),
        ).inOrder()
        assertThat(analytics.loggedEvents.map { it.type }.distinct()).containsExactly("control_component_via_provider_activated")
        assertThat(analytics.loggedEvents.map { it.extras.single().value }).containsExactly("true", "false", "false").inOrder()
    }

    @Test
    fun givenDuplicateClassWithDifferentFlags_whenBlocks_thenLastFlagDecidesItsRequest() = runTest {
        val component = status("A")
        seed(component)
        val input = input("A" to false, "A" to true)

        assertThat(call("blocks", PACKAGE_NAME, input)).isSameInstanceAs(input)

        assertThat(repository.requests.single().goal).isEqualTo(ControlGoal.BLOCK)
        assertThat(analytics.loggedEvents).hasSize(2)
    }

    @Test
    fun givenUnknownComponentNames_whenBlocks_thenIgnoresThemAndReturnsInput() = runTest {
        val known = status("Known")
        val notInPackage = status("Other", packageName = "com.example.other")
        seed(known, notInPackage)
        val input = input("Unknown" to true, "" to false, "Known" to true)

        assertThat(call("blocks", PACKAGE_NAME, input)).isSameInstanceAs(input)

        assertThat(repository.requests.single().components).containsExactly(known.declaration)
        assertThat(analytics.loggedEvents).hasSize(3)
    }

    @Test
    fun givenNoMatchingComponentsOrEmptyList_whenBlocks_thenReturnsInputWithoutConnecting() = runTest {
        channel.setState(DENIED)
        val unknown = input("Unknown" to true)
        val empty = input()

        assertThat(call("blocks", PACKAGE_NAME, unknown)).isSameInstanceAs(unknown)
        assertThat(call("blocks", PACKAGE_NAME, empty)).isSameInstanceAs(empty)
        assertThat(repository.requests).isEmpty()
        assertThat(channel.quietConnectCount).isEqualTo(0)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenBlockersOwnComponent_whenBlocks_thenReturnsInputForRepositoryRefusal() = runTest {
        val ownPackage = application.packageName
        val own = status("Own", packageName = ownPackage)
        seed(own)
        val key = ComponentKey(own.declaration.id, OWN_USER)
        repository.controlEvents = {
            listOf(
                ControlEvent.PackageDone(
                    ownPackage,
                    listOf(ComponentOutcome(key, own.state, ControlResult.FAILED, mapOf(PACKAGE_MANAGER to ControlFailure.NotPermitted(null)))),
                ),
                ControlEvent.Finished(achieved = 0, partial = 0, failed = 1),
            )
        }
        val input = Bundle().apply {
            putString("cmp_list", """{"pkg":"ignored","components":[{"type":"ignored","name":"$ownPackage.Own","block":true}]}""")
        }

        assertThat(call("blocks", ownPackage, input)).isSameInstanceAs(input)

        assertThat(repository.requests.single().components).containsExactly(own.declaration)
        assertThat(repository.component(key).first()).isEqualTo(own)
    }

    @Test
    fun givenControllerCompletesWithoutChangingState_whenBlocks_thenReturnsInput() = runTest {
        val component = status("A")
        seed(component)
        repository.controlEvents = {
            listOf(
                ControlEvent.PackageDone(
                    PACKAGE_NAME,
                    listOf(
                        ComponentOutcome(
                            ComponentKey(component.declaration.id, OWN_USER),
                            component.state,
                            ControlResult.FAILED,
                            mapOf(INTENT_FIREWALL to ControlFailure.RulesUnreadable),
                        ),
                    ),
                ),
            )
        }
        val input = input("A" to true)

        assertThat(call("blocks", PACKAGE_NAME, input)).isSameInstanceAs(input)
        assertThat(analytics.loggedEvents).hasSize(1)
    }

    @Test
    fun givenControlThrows_whenBlocks_thenReturnsNull() = runTest {
        seed(status("A"))
        repository.controlEvents = { error("Control failed") }

        assertThat(call("blocks", PACKAGE_NAME, input("A" to true))).isNull()
        assertThat(analytics.loggedEvents).isEmpty()
    }

    @Test
    fun givenAccessMissing_whenBlocks_thenReturnsNullWithoutPrompting() = runTest {
        seed(status("A"))
        channel.setState(DENIED)

        assertThat(call("blocks", PACKAGE_NAME, input("A" to true))).isNull()

        assertQuietConnect()
        assertThat(repository.requests).isEmpty()
    }

    @Test
    fun givenRefreshCannotFinishBeforeDeadline_whenBlocks_thenReturnsNullWhileRefreshIsPending() = runTest {
        seed(status("A"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        refresh = {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
            }
            ReadResult.Success(Unit)
        }

        try {
            assertThat(call("blocks", PACKAGE_NAME, input("A" to true))).isNull()
            assertThat(started.isCompleted).isTrue()
            assertThat(repository.requests).isEmpty()
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun givenMissingArgumentsOrUnknownMethod_whenCalling_thenReturnsNull() = runTest {
        assertThat(call("getComponents", null)).isNull()
        assertThat(call("unknown", PACKAGE_NAME)).isNull()
        assertThat(call("blocks", null, input("A" to true))).isNull()
        assertThat(call("blocks", PACKAGE_NAME)).isNull()
        assertThat(call("blocks", PACKAGE_NAME, Bundle())).isNull()
        assertThat(repository.requests).isEmpty()
        assertThat(repository.refreshedApps).isEmpty()
    }

    @Test
    fun givenMalformedOrNonconformingJson_whenBlocks_thenReturnsNullWithoutWork() = runTest {
        val invalidJson = listOf(
            "not json",
            """{"pkg":"p","components":[],"extra":1}""",
            """{"pkg":"p","components":[{"type":"t","name":"X"}]}""",
        )
        for (json in invalidJson) {
            assertThat(call("blocks", PACKAGE_NAME, Bundle().apply { putString("cmp_list", json) })).isNull()
        }
        assertThat(repository.refreshedApps).isEmpty()
        assertThat(repository.requests).isEmpty()
        assertThat(channel.quietConnectCount).isEqualTo(0)
    }

    @Test
    fun givenUntrustedUid_whenCalling_thenRejectsBeforeAnyWork() = runTest {
        withContext(Dispatchers.IO) {
            ShadowBinder.setCallingUid(Process.myUid() + 1)
            try {
                assertThrows(SecurityException::class.java) { provider.call("getComponents", PACKAGE_NAME, null) }
                assertThrows(SecurityException::class.java) { provider.call("blocks", PACKAGE_NAME, input("A" to true)) }
                assertThrows(SecurityException::class.java) { provider.call("unknown", null, null) }
            } finally {
                ShadowBinder.setCallingUid(Process.myUid())
            }
        }

        assertThat(application.entryPointReads).isEqualTo(0)
        assertThat(repository.refreshedApps).isEmpty()
        assertThat(repository.requests).isEmpty()
        assertThat(channel.quietConnectCount).isEqualTo(0)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenMainThread_whenCalling_thenDoesNotStartWork() {
        assertThat(provider.call("getComponents", PACKAGE_NAME, null)).isNull()
        assertThat(application.entryPointReads).isEqualTo(0)
    }

    @Test
    fun givenProviderInfo_whenReadingType_thenItIsTheComponentCursorItemType() {
        assertThat(provider.getType(Uri.parse("content://x/y")))
            .isEqualTo("vnd.android.cursor.item/vnd.com.merxury.blocker.component")
    }

    private suspend fun call(method: String, arg: String?, extras: Bundle? = null): Bundle? = withContext(Dispatchers.IO) { provider.call(method, arg, extras) }

    private suspend fun assertComponents(expected: String) {
        val result = checkNotNull(call("getComponents", PACKAGE_NAME))
        assertThat(result.keySet()).containsExactly("cmp_list")
        assertThat(result.getString("cmp_list")).isEqualTo(expected)
    }

    private fun assertQuietConnect() {
        assertThat(channel.quietConnectCount).isEqualTo(1)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    private fun seed(vararg components: ComponentStatus) = repository.sendComponents(OWN_USER, components.toList())

    private fun status(
        name: String,
        pm: PmSetting = PmSetting.DEFAULT,
        ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
        enabledInManifest: Boolean = true,
        type: ComponentType = ComponentType.SERVICE,
        packageName: String = PACKAGE_NAME,
    ) = ComponentStatus(
        ComponentDeclaration(ComponentId(packageName, "$packageName.$name"), type, exported = true, enabledInManifest = enabledInManifest),
        ControlState(pm, ifw),
    )

    private fun input(vararg components: Pair<String, Boolean>): Bundle {
        val entries = components.joinToString(",") { (name, block) ->
            val className = if (name.isEmpty()) "" else "$PACKAGE_NAME.$name"
            """{"type":"ignored","name":"$className","block":$block}"""
        }
        return Bundle().apply { putString("cmp_list", """{"pkg":"com.example.other","components":[$entries]}""") }
    }

    private companion object {
        const val PACKAGE_NAME = "com.example.app"
        const val EMPTY_JSON = """{"pkg":"com.example.app","components":[]}"""
        const val TRACKER_JSON = """{"pkg":"com.example.app","components":[{"type":"com.example.app","name":"com.example.app.Tracker","block":true}]}"""
        val OWN_USER = UserId(10)
        val APP = AppKey(PACKAGE_NAME, OWN_USER)
        val DENIED = ChannelState.Unavailable(UnavailableReason.DENIED)
    }
}
