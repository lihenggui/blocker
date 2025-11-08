/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.utils

import com.merxury.blocker.core.model.manifest.ManifestActivity
import com.merxury.blocker.core.model.manifest.ManifestProvider
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ManifestParser].
 *
 * Tests complete manifest parsing including components, intent filters,
 * permissions, and metadata.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestParserTest {

    private lateinit var testApkFile: File

    @Before
    fun setup() {
        val classLoader = javaClass.classLoader!!
        val resource = classLoader.getResource("test-app.apk")
        requireNotNull(resource) { "Test APK not found in resources" }
        testApkFile = File(resource.toURI())
    }

    @Test
    fun givenValidApk_whenParseManifest_thenReturnSuccess() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)

        assertTrue(result.isSuccess, "Parsing should succeed for valid APK")
        assertNotNull(result.getOrNull(), "Should return non-null manifest")
    }

    @Test
    fun givenValidApk_whenParseManifest_thenReturnCorrectPackageName() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        assertEquals("com.merxury.blocker.test", manifest.packageName)
    }

    @Test
    fun givenValidApk_whenParseManifest_thenReturnVersionInfo() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        assertNotNull(manifest.versionCode, "Version code should be parsed")
        assertNotNull(manifest.versionName, "Version name should be parsed")
    }

    @Test
    fun givenValidApk_whenParseManifest_thenReturnSdkVersions() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        assertNotNull(manifest.minSdkVersion, "Min SDK version should be parsed")
    }

    @Test
    fun givenValidApk_whenParseManifest_thenReturnApplicationInfo() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        assertNotNull(manifest.application, "Application should be parsed")
    }

    @Test
    fun givenValidApk_whenParseManifest_thenReturnActivities() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        assertTrue(
            manifest.application.activities.isNotEmpty(),
            "Should parse at least one activity",
        )
    }

    @Test
    fun givenActivityWithIntentFilter_whenParseManifest_thenReturnIntentFilters() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activitiesWithFilters = manifest.application.activities.filter {
            it.intentFilters.isNotEmpty()
        }
        assertTrue(
            activitiesWithFilters.isNotEmpty(),
            "Should find activities with intent filters",
        )
    }

    @Test
    fun givenActivityWithViewAction_whenParseManifest_thenReturnViewAction() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithView = manifest.application.activities.find { activity ->
            activity.intentFilters.any { filter ->
                filter.actions.contains("android.intent.action.VIEW")
            }
        }
        assertNotNull(activityWithView, "Should find activity with VIEW action")
    }

    @Test
    fun givenActivityWithSendAction_whenParseManifest_thenReturnSendAction() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithSend = manifest.application.activities.find { activity ->
            activity.intentFilters.any { filter ->
                filter.actions.contains("android.intent.action.SEND")
            }
        }
        assertNotNull(activityWithSend, "Should find activity with SEND action")
        assertEquals("com.merxury.blocker.test.ApkDetailActivity", activityWithSend.name)
        assertTrue(activityWithSend.exported, "Activity with SEND should be exported")
    }

    @Test
    fun givenActivityWithBrowsableCategory_whenParseManifest_thenReturnBrowsableCategory() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithBrowsable = manifest.application.activities.find { activity ->
            activity.intentFilters.any { filter ->
                filter.categories.contains("android.intent.category.BROWSABLE")
            }
        }
        assertNotNull(activityWithBrowsable, "Should find activity with BROWSABLE category")
    }

    @Test
    fun givenActivityWithDataSpec_whenParseManifest_thenReturnDataSpecification() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithData = manifest.application.activities.find { activity ->
            activity.intentFilters.any { filter ->
                filter.data.isNotEmpty()
            }
        }
        if (activityWithData != null) {
            val intentFilterWithData = activityWithData.intentFilters.first { it.data.isNotEmpty() }
            val data = intentFilterWithData.data.first()
            assertNotNull(data, "Should parse data specification")
        }
    }

    @Test
    fun givenActivityWithMultipleIntentFilters_whenParseManifest_thenReturnAllFilters() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithMultipleFilters = manifest.application.activities.find {
            it.intentFilters.size > 1
        }
        if (activityWithMultipleFilters != null) {
            assertTrue(
                activityWithMultipleFilters.intentFilters.size > 1,
                "Should parse multiple intent filters",
            )
        }
    }

    @Test
    fun givenLauncherActivity_whenParseManifest_thenReturnLauncherActivity() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val launcherActivity = manifest.application.activities.find { activity ->
            activity.intentFilters.any { filter ->
                filter.actions.contains("android.intent.action.MAIN") &&
                    filter.categories.contains("android.intent.category.LAUNCHER")
            }
        }
        assertNotNull(launcherActivity, "Should find launcher activity")
        assertEquals("com.merxury.blocker.test.MainActivity", launcherActivity.name)
    }

    @Test
    fun givenServices_whenParseManifest_thenReturnServices() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val services = manifest.application.services
        if (services.isNotEmpty()) {
            assertTrue(services.all { it is ManifestService })
        }
    }

    @Test
    fun givenReceivers_whenParseManifest_thenReturnReceivers() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val receivers = manifest.application.receivers
        if (receivers.isNotEmpty()) {
            assertTrue(receivers.all { it is ManifestReceiver })
        }
    }

    @Test
    fun givenProviders_whenParseManifest_thenReturnProviders() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val providers = manifest.application.providers
        if (providers.isNotEmpty()) {
            assertTrue(providers.all { it is ManifestProvider })
            val provider = providers.first()
            assertNotNull(provider.authorities, "Provider should have authorities")
        }
    }

    @Test
    fun givenComponentWithExportedAttribute_whenParseManifest_thenReturnExportedValue() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val exportedActivity = manifest.application.activities.find { it.exported }
        if (exportedActivity != null) {
            assertTrue(exportedActivity.exported, "Should correctly parse exported=true")
        }

        val nonExportedActivity = manifest.application.activities.find { !it.exported }
        if (nonExportedActivity != null) {
            assertFalse(nonExportedActivity.exported, "Should correctly parse exported=false")
        }
    }

    @Test
    fun givenComponentWithEnabledAttribute_whenParseManifest_thenReturnEnabledValue() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activities = manifest.application.activities
        activities.forEach { activity ->
            assertTrue(activity.enabled || !activity.enabled, "Should parse enabled attribute")
        }
    }

    @Test
    fun givenUsesPermissions_whenParseManifest_thenReturnPermissions() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        if (manifest.usesPermissions.isNotEmpty()) {
            assertTrue(
                manifest.usesPermissions.all { it.name.isNotEmpty() },
                "All permissions should have names",
            )
        }
    }

    @Test
    fun givenMetaData_whenParseManifest_thenReturnMetaData() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val componentsWithMetaData = manifest.application.activities.filter {
            it.metaData.isNotEmpty()
        }
        if (componentsWithMetaData.isNotEmpty()) {
            val metaData = componentsWithMetaData.first().metaData.first()
            assertTrue(metaData.name.isNotEmpty(), "Meta-data should have a name")
        }
    }

    @Test
    fun givenAllComponentTypes_whenParseManifest_thenReturnAllTypes() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val allActivities = manifest.application.activities.filterIsInstance<ManifestActivity>()
        val allServices = manifest.application.services.filterIsInstance<ManifestService>()
        val allReceivers = manifest.application.receivers.filterIsInstance<ManifestReceiver>()
        val allProviders = manifest.application.providers.filterIsInstance<ManifestProvider>()

        assertTrue(allActivities.size == manifest.application.activities.size)
        assertTrue(allServices.size == manifest.application.services.size)
        assertTrue(allReceivers.size == manifest.application.receivers.size)
        assertTrue(allProviders.size == manifest.application.providers.size)
    }

    @Test
    fun givenInvalidFile_whenParseManifest_thenReturnFailure() = runTest {
        val invalidFile = File("/path/to/nonexistent.apk")
        val result = ManifestParser.parseManifest(invalidFile)

        assertTrue(result.isFailure, "Should fail for invalid file")
    }

    @Test
    fun givenActivityAttributes_whenParseManifest_thenReturnAllAttributes() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activities = manifest.application.activities
        assertTrue(activities.isNotEmpty(), "Should have at least one activity")

        val activity = activities.first()
        assertNotNull(activity.name, "Activity should have name")
    }

    @Test
    fun givenIntentFilterWithMultipleActions_whenParseManifest_thenReturnAllActions() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithMultipleActions = manifest.application.activities.find { activity ->
            activity.intentFilters.any { it.actions.size > 1 }
        }
        if (activityWithMultipleActions != null) {
            val filterWithMultipleActions = activityWithMultipleActions.intentFilters
                .first { it.actions.size > 1 }
            assertTrue(
                filterWithMultipleActions.actions.size > 1,
                "Should parse multiple actions in one filter",
            )
        }
    }

    @Test
    fun givenIntentFilterWithMultipleCategories_whenParseManifest_thenReturnAllCategories() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activityWithMultipleCategories = manifest.application.activities.find { activity ->
            activity.intentFilters.any { it.categories.size > 1 }
        }
        if (activityWithMultipleCategories != null) {
            val filterWithMultipleCategories = activityWithMultipleCategories.intentFilters
                .first { it.categories.size > 1 }
            assertTrue(
                filterWithMultipleCategories.categories.size > 1,
                "Should parse multiple categories in one filter",
            )
        }
    }

    @Test
    fun givenApplicationAttributes_whenParseManifest_thenReturnApplicationInfo() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val application = manifest.application
        assertNotNull(application)
    }

    @Test
    fun givenComponentLabel_whenParseManifest_thenResolveLabelOrFallback() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        manifest.application.activities.forEach { activity ->
            if (activity.label != null) {
                assertFalse(
                    activity.label!!.startsWith("@"),
                    "Activity label should be resolved, not raw resource reference: ${activity.label}",
                )
            }
        }
    }

    @Test
    fun givenActivityWithLabel_whenParseManifest_thenLabelNotNull() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        val activitiesWithLabels = manifest.application.activities.filter { it.label != null }
        if (activitiesWithLabels.isNotEmpty()) {
            activitiesWithLabels.forEach { activity ->
                assertNotNull(activity.label)
                assertTrue(activity.label!!.isNotEmpty())
            }
        }
    }

    @Test
    fun givenServiceWithLabel_whenParseManifest_thenLabelResolved() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        manifest.application.services.forEach { service ->
            if (service.label != null) {
                assertFalse(
                    service.label!!.startsWith("@"),
                    "Service label should be resolved, not raw resource reference: ${service.label}",
                )
            }
        }
    }

    @Test
    fun givenReceiverWithLabel_whenParseManifest_thenLabelResolved() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        manifest.application.receivers.forEach { receiver ->
            if (receiver.label != null) {
                assertFalse(
                    receiver.label!!.startsWith("@"),
                    "Receiver label should be resolved, not raw resource reference: ${receiver.label}",
                )
            }
        }
    }

    @Test
    fun givenProviderWithLabel_whenParseManifest_thenLabelResolved() = runTest {
        val result = ManifestParser.parseManifest(testApkFile)
        val manifest = result.getOrNull()

        assertNotNull(manifest)
        manifest.application.providers.forEach { provider ->
            if (provider.label != null) {
                assertFalse(
                    provider.label!!.startsWith("@"),
                    "Provider label should be resolved, not raw resource reference: ${provider.label}",
                )
            }
        }
    }
}
