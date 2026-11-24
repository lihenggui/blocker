// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the ContentProvider contract LibChecker uses: `call("getComponents", pkg)` and
 * `call("blocks", pkg, extras)`, with the component list as JSON under the "cmp_list" bundle key.
 *
 * The provider finds its repository through a Hilt entry point on the Application. The test
 * Application below supplies that entry point directly, so no Hilt component is generated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ComponentProviderContractTest.TestApp::class)
class ComponentProviderContractTest {

    class RecordingRepository(
        private val delegate: TestComponentRepository = TestComponentRepository(),
        private val controlResult: Boolean = true,
    ) : ComponentRepository by delegate {
        data class Control(val component: ComponentInfo, val newState: Boolean)

        val controls = mutableListOf<Control>()

        suspend fun seed(components: List<ComponentInfo>) = delegate.saveComponents(components)

        override fun controlComponent(
            component: ComponentInfo,
            newState: Boolean,
        ): Flow<Boolean> {
            controls += Control(component, newState)
            return flowOf(controlResult)
        }
    }

    // Hilt only accepts a component that is marked as generated.
    interface TestEntryPoint :
        ComponentProvider.ComponentRepositoryEntryPoint,
        GeneratedComponent

    class TestApp :
        Application(),
        GeneratedComponentManager<TestEntryPoint> {
        override fun generatedComponent() = entryPoint
    }

    private lateinit var repository: RecordingRepository
    private lateinit var analytics: TestAnalyticsHelper
    private lateinit var provider: ComponentProvider

    @Before
    fun setUp() {
        repository = RecordingRepository()
        analytics = TestAnalyticsHelper()
        entryPoint = object : TestEntryPoint {
            override fun componentRepository(): ComponentRepository = repository

            override fun analyticsHelper(): AnalyticsHelper = analytics
        }
        provider = Robolectric.buildContentProvider(ComponentProvider::class.java)
            .create("com.merxury.blocker.provider.test")
            .get()
        // Make sure the provider reads the Application set up for this test.
        assertThat(ApplicationProvider.getApplicationContext<Application>()).isInstanceOf(TestApp::class.java)
    }

    private fun component(
        name: String,
        pmBlocked: Boolean = false,
        ifwBlocked: Boolean = false,
        packageName: String = "com.example.app",
    ) = ComponentInfo(
        packageName = packageName,
        name = name,
        type = ComponentType.SERVICE,
        pmBlocked = pmBlocked,
        ifwBlocked = ifwBlocked,
    )

    private fun getComponentsJson(packageName: String): String {
        val result = checkNotNull(provider.call("getComponents", packageName, null))
        return checkNotNull(result.getString("cmp_list"))
    }

    @Test
    fun givenBlockedComponents_whenGetComponents_thenBundleHoldsJsonUnderCmpListKey() {
        runBlocking {
            repository.seed(listOf(component("com.example.app.Tracker", pmBlocked = true)))
        }

        val result = checkNotNull(provider.call("getComponents", "com.example.app", null))

        assertThat(result.keySet()).containsExactly("cmp_list")
        assertThat(result.getString("cmp_list")).isNotEmpty()
    }

    @Test
    fun givenBlockedComponents_whenGetComponents_thenJsonShapeIsPkgAndComponents() {
        runBlocking {
            repository.seed(listOf(component("com.example.app.Tracker", pmBlocked = true)))
        }

        val root = Json.parseToJsonElement(getComponentsJson("com.example.app")).jsonObject

        assertThat(root.keys).containsExactly("pkg", "components")
        assertThat(root.getValue("pkg").jsonPrimitive.content).isEqualTo("com.example.app")
        val entry = root.getValue("components").jsonArray.single().jsonObject
        assertThat(entry.keys).containsExactly("type", "name", "block")
        assertThat(entry.getValue("name").jsonPrimitive.content).isEqualTo("com.example.app.Tracker")
        assertThat(entry.getValue("block").jsonPrimitive.boolean).isTrue()
    }

    @Test
    fun givenBlockedComponents_whenGetComponents_thenTypeCurrentlyCarriesPackageName() {
        // Known quirk, not desired behaviour: the provider fills "type" with the component's
        // package name instead of its component type. Expected to change; update this test then.
        runBlocking {
            repository.seed(listOf(component("com.example.app.Tracker", pmBlocked = true)))
        }

        val root = Json.parseToJsonElement(getComponentsJson("com.example.app")).jsonObject
        val entry = root.getValue("components").jsonArray.single().jsonObject

        assertThat(entry.getValue("type").jsonPrimitive.content).isEqualTo("com.example.app")
    }

    @Test
    fun givenMixedComponents_whenGetComponents_thenOnlyBlockedOnesAreListedWithBlockTrue() {
        runBlocking {
            repository.seed(
                listOf(
                    component("com.example.app.Enabled"),
                    component("com.example.app.PmOff", pmBlocked = true),
                    component("com.example.app.IfwOff", ifwBlocked = true),
                    component("com.example.app.BothOff", pmBlocked = true, ifwBlocked = true),
                    component("com.other.Other", pmBlocked = true, packageName = "com.other"),
                ),
            )
        }

        val root = Json.parseToJsonElement(getComponentsJson("com.example.app")).jsonObject
        val entries = root.getValue("components").jsonArray.map { it.jsonObject }

        assertThat(entries.map { it.getValue("name").jsonPrimitive.content }).containsExactly(
            "com.example.app.PmOff",
            "com.example.app.IfwOff",
            "com.example.app.BothOff",
        ).inOrder()
        assertThat(entries.map { it.getValue("block").jsonPrimitive.boolean }).containsExactly(true, true, true)
    }

    @Test
    fun givenNoBlockedComponents_whenGetComponents_thenJsonHasEmptyComponentsArray() {
        runBlocking { repository.seed(listOf(component("com.example.app.Enabled"))) }

        assertThat(getComponentsJson("com.example.app")).isEqualTo(
            """{"pkg":"com.example.app","components":[]}""",
        )
    }

    @Test
    fun givenBlockedComponent_whenGetComponents_thenJsonIsCompactWithFieldOrderTypeNameBlock() {
        runBlocking {
            repository.seed(listOf(component("com.example.app.Tracker", pmBlocked = true)))
        }

        assertThat(getComponentsJson("com.example.app")).isEqualTo(
            """{"pkg":"com.example.app","components":""" +
                """[{"type":"com.example.app","name":"com.example.app.Tracker","block":true}]}""",
        )
    }

    @Test
    fun givenNullPackageName_whenGetComponents_thenReturnsNull() {
        assertThat(provider.call("getComponents", null, null)).isNull()
    }

    @Test
    fun givenUnknownMethod_whenCalling_thenReturnsNull() {
        assertThat(provider.call("unknown", "com.example.app", null)).isNull()
    }

    @Test
    fun givenCmpListJson_whenBlocks_thenEachComponentIsControlledWithInvertedBlockFlag() {
        val json = """
            {"pkg":"com.example.app","components":[
                {"type":"ignored","name":"com.example.app.A","block":true},
                {"type":"ignored","name":"com.example.app.B","block":false}
            ]}
        """.trimIndent()

        val result = provider.call("blocks", "com.example.app", Bundle().apply { putString("cmp_list", json) })

        assertThat(repository.controls.map { it.component.name to it.newState }).containsExactly(
            "com.example.app.A" to false,
            "com.example.app.B" to true,
        ).inOrder()
        // The package comes from the call argument, not from "pkg" in the JSON, and the type is
        // always ACTIVITY because the controller looks it up itself.
        assertThat(repository.controls.map { it.component.packageName }.toSet()).containsExactly("com.example.app")
        assertThat(repository.controls.map { it.component.type }.toSet()).containsExactly(ComponentType.ACTIVITY)
        assertThat(analytics.enableStatisticsUpdates).isEmpty()
        // The provider hands the request bundle back, although callers do not use it.
        assertThat(result?.getString("cmp_list")).isEqualTo(json)
    }

    @Test
    fun givenPackageArgumentDiffersFromJson_whenBlocks_thenCallArgumentWins() {
        val json = """{"pkg":"com.other","components":[{"type":"t","name":"X","block":true}]}"""

        provider.call("blocks", "com.example.app", Bundle().apply { putString("cmp_list", json) })

        assertThat(repository.controls.single().component.packageName).isEqualTo("com.example.app")
    }

    @Test
    fun givenMissingExtrasOrKey_whenBlocks_thenReturnsNullAndControlsNothing() {
        assertThat(provider.call("blocks", "com.example.app", null)).isNull()
        assertThat(provider.call("blocks", "com.example.app", Bundle())).isNull()
        assertThat(provider.call("blocks", null, Bundle())).isNull()
        assertThat(repository.controls).isEmpty()
    }

    @Test
    fun givenMalformedJson_whenBlocks_thenReturnsNull() {
        val result = provider.call("blocks", "com.example.app", Bundle().apply { putString("cmp_list", "not json") })

        assertThat(result).isNull()
        assertThat(repository.controls).isEmpty()
    }

    @Test
    fun givenJsonWithUnknownKey_whenBlocks_thenReturnsNullBecauseDecodingIsStrict() {
        val json = """{"pkg":"p","components":[],"extra":1}"""

        val result = provider.call("blocks", "com.example.app", Bundle().apply { putString("cmp_list", json) })

        assertThat(result).isNull()
    }

    @Test
    fun givenComponentWithoutBlockKey_whenBlocks_thenReturnsNullBecauseNoFieldHasADefault() {
        val json = """{"pkg":"p","components":[{"type":"t","name":"X"}]}"""

        val result = provider.call("blocks", "com.example.app", Bundle().apply { putString("cmp_list", json) })

        assertThat(result).isNull()
        assertThat(repository.controls).isEmpty()
    }

    @Test
    fun givenProviderInfo_whenReadingType_thenItIsTheComponentCursorItemType() {
        assertThat(provider.getType(android.net.Uri.parse("content://x/y")))
            .isEqualTo("vnd.android.cursor.item/vnd.com.merxury.blocker.component")
    }

    companion object {
        lateinit var entryPoint: TestEntryPoint
    }
}
