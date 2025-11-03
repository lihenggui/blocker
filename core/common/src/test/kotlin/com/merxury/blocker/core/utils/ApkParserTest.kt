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

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ApkParserTest {

    private lateinit var testApkFile: File

    @Before
    fun setup() {
        val classLoader = javaClass.classLoader!!
        val resource = classLoader.getResource("test-app.apk")
        requireNotNull(resource) { "Test APK not found in resources" }
        testApkFile = File(resource.toURI())
    }

    @Test
    fun givenValidApk_whenGetActivitiesWithIntentFilters_thenReturnNonEmptyList() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        assertTrue(result.isNotEmpty(), "Should return activities with matching intent filters")
    }

    @Test
    fun givenApkWithViewAction_whenGetActivitiesWithIntentFilters_thenReturnActivity() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithView = result.find { activity ->
            activity.intentFilters.any { filter ->
                filter.actions.contains("android.intent.action.VIEW")
            }
        }
        assertNotNull(activityWithView, "Should find at least one activity with VIEW action")
        assertEquals("com.merxury.blocker.test", activityWithView.packageName)
    }

    @Test
    fun givenApkWithSendAction_whenGetActivitiesWithIntentFilters_thenReturnActivity() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithSend = result.find { activity ->
            activity.intentFilters.any { filter ->
                filter.actions.contains("android.intent.action.SEND")
            }
        }
        assertNotNull(activityWithSend, "Should find activity with SEND action")
        assertEquals("com.merxury.blocker.test.ApkDetailActivity", activityWithSend.name)
        assertTrue(activityWithSend.exported, "Activity with SEND should be exported")
    }

    @Test
    fun givenApkWithSendMultipleAction_whenGetActivitiesWithIntentFilters_thenReturnActivity() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithSendMultiple = result.find { activity ->
            activity.intentFilters.any { filter ->
                filter.actions.contains("android.intent.action.SEND_MULTIPLE")
            }
        }
        if (activityWithSendMultiple != null) {
            assertTrue(activityWithSendMultiple.exported, "Activity with SEND_MULTIPLE should be exported")
            assertEquals("com.merxury.blocker.test", activityWithSendMultiple.packageName)
        }
    }

    @Test
    fun givenApkWithBrowsableCategory_whenGetActivitiesWithIntentFilters_thenReturnActivity() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithBrowsable = result.find { activity ->
            activity.intentFilters.any { filter ->
                filter.categories.contains("android.intent.category.BROWSABLE")
            }
        }
        assertNotNull(activityWithBrowsable, "Should find activity with BROWSABLE category")
        assertTrue(activityWithBrowsable.exported, "Activity with BROWSABLE should be exported")
    }

    @Test
    fun givenApkWithDataElements_whenGetActivitiesWithIntentFilters_thenParseDataCorrectly() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithData = result.find { it.name == "com.merxury.blocker.test.ApkDetailActivity" }
        assertNotNull(activityWithData, "Should find ApkDetailActivity")

        val filterWithData = activityWithData.intentFilters.find { filter ->
            filter.actions.contains("android.intent.action.VIEW")
        }
        assertNotNull(filterWithData, "Should have intent filter with VIEW action")

        assertTrue(filterWithData.data.isNotEmpty(), "Should have data elements")
        assertTrue(
            filterWithData.data.any { it.scheme == "content" },
            "Should have data with content scheme",
        )
        assertTrue(
            filterWithData.data.any { it.mimeType == "application/vnd.android.package-archive" },
            "Should have data with APK mime type",
        )
    }

    @Test
    fun givenApkWithExportedAttribute_whenGetActivitiesWithIntentFilters_thenReturnCorrectExportedValue() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val nonExportedActivity = result.find { it.name == "com.merxury.blocker.test.NonExportedActivity" }
        assertNotNull(nonExportedActivity, "Should find NonExportedActivity")
        assertEquals(false, nonExportedActivity.exported, "NonExportedActivity should not be exported")
    }

    @Test
    fun givenApkWithLabel_whenGetActivitiesWithIntentFilters_thenReturnLabel() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithLabel = result.find { it.name == "com.merxury.blocker.test.ApkDetailActivity" }
        assertNotNull(activityWithLabel, "Should find ApkDetailActivity")
        assertEquals("Detail", activityWithLabel.label)
    }

    @Test
    fun givenApkWithMainActivity_whenGetActivitiesWithIntentFilters_thenNotIncludeMainActivity() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val mainActivity = result.find { it.name == "com.merxury.blocker.test.MainActivity" }
        assertEquals(null, mainActivity, "Should not include MainActivity with only LAUNCHER action")
    }

    @Test
    fun givenApkWithMultipleIntentFilters_whenGetActivitiesWithIntentFilters_thenReturnAllFilters() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val activityWithMultipleFilters = result.find { it.name == "com.merxury.blocker.test.ApkDetailActivity" }
        assertNotNull(activityWithMultipleFilters, "Should find ApkDetailActivity")
        assertTrue(
            activityWithMultipleFilters.intentFilters.size >= 2,
            "Should have multiple intent filters",
        )

        val hasViewFilter = activityWithMultipleFilters.intentFilters.any {
            it.actions.contains("android.intent.action.VIEW")
        }
        val hasSendFilter = activityWithMultipleFilters.intentFilters.any {
            it.actions.contains("android.intent.action.SEND")
        }
        assertTrue(hasViewFilter, "Should have VIEW filter")
        assertTrue(hasSendFilter, "Should have SEND filter")
    }

    @Test
    fun givenApkWithComplexData_whenGetActivitiesWithIntentFilters_thenParseSchemeAndHost() = runTest {
        val result = ApkParser.getActivitiesWithIntentFilters(testApkFile)

        val bridgeActivity = result.find { it.name == "com.merxury.blocker.test.BridgeActivity" }
        assertNotNull(bridgeActivity, "Should find BridgeActivity")

        val filter = bridgeActivity.intentFilters.first()
        assertTrue(filter.data.any { it.scheme == "lc" && it.host == "bridge" })
    }
}
