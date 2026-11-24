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

package com.merxury.blocker.feature.debloater.impl

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.IntentFilterData
import com.merxury.blocker.core.model.manifest.ManifestActivity
import com.merxury.blocker.core.model.manifest.ManifestApplication
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestProvider
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import org.junit.Test
import com.merxury.blocker.feature.debloater.impl.ComponentClassification as Kind

class DebloaterClassificationTest {
    @Test
    fun givenFixedManifest_whenClassified_thenKeepLegacyComponentsAndKindsInOrder() {
        val actual = classifyDebloatableComponents(classificationManifest.packageName, classificationManifest, classificationPermissions)
            .map { Expected(it.id.className, it.displayName, it.kinds) }
        assertThat(actual).containsExactly(
            Expected("example.Share", "Share handler", setOf(Kind.SHAREABLE, Kind.EXPORTED_NO_PERM, Kind.WAKELOCK)),
            Expected("example.LaunchFromLink", "LaunchFromLink", setOf(Kind.LAUNCHER, Kind.DEEPLINK, Kind.WAKELOCK)),
            Expected("example.ViewOnly", "ViewOnly", setOf(Kind.WAKELOCK)),
            Expected("example.MyPushService", "MyPushService", setOf(Kind.PUSH_SERVICE, Kind.FOREGROUND_SERVICE, Kind.WAKELOCK)),
            Expected("example.Boot", "Boot", setOf(Kind.AUTO_START, Kind.WAKELOCK)),
            Expected("example.Provider", "Provider", setOf(Kind.DANGEROUS_PROVIDER, Kind.EXPORTED_NO_PERM, Kind.WAKELOCK)),
        ).inOrder()
    }

    @Test
    fun givenBareClassName_whenClassified_thenQualifyTheIdAndKeepTheDisplayName() {
        val manifest = AndroidManifest("example", application = ManifestApplication(services = listOf(ManifestService("Bare"))))

        val row = classifyDebloatableComponents("example", manifest, emptyList()).single()

        assertThat(row.id.className).isEqualTo("example.Bare")
        assertThat(row.className).isEqualTo("Bare")
        assertThat(row.displayName).isEqualTo("Bare")
    }

    @Test
    fun givenEquivalentClassNames_whenClassified_thenKeepTheFirstDeclarationPerId() {
        val manifest = AndroidManifest(
            "example",
            application = ManifestApplication(
                services = listOf(ManifestService(".Foo", label = "First"), ManifestService("example.Foo", label = "Duplicate"), ManifestService(".Bar")),
            ),
        )

        val rows = classifyDebloatableComponents("example", manifest, emptyList())

        assertThat(rows.map { it.displayName }).containsExactly("First", "Bar").inOrder()
        assertThat(rows.map { it.id.className }).containsExactly("example.Foo", "example.Bar").inOrder()
    }

    @Test
    fun givenWakeOrExactAlarmPermission_whenClassified_thenEveryRowHasTheWakelockKind() {
        for (permission in listOf("WAKE_LOCK", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM")) {
            val rows = classifyDebloatableComponents("example", classificationManifest, listOf("android.permission.$permission"))

            assertThat(rows).hasSize(6)
            rows.forEach { assertThat(it.kinds).contains(Kind.WAKELOCK) }
        }
    }

    @Test
    fun givenEachAutoStartAction_whenClassified_thenEveryMatchingReceiverHasTheAutoStartKind() {
        val actions = listOf("BOOT_COMPLETED", "LOCKED_BOOT_COMPLETED", "MY_PACKAGE_REPLACED", "USER_PRESENT", "PACKAGE_REPLACED")
        val receivers = actions.map { action ->
            ManifestReceiver(name = ".$action", intentFilters = listOf(ManifestIntentFilter(actions = listOf("android.intent.action.$action"))))
        }
        val manifest = AndroidManifest("example", application = ManifestApplication(receivers = receivers))

        val rows = classifyDebloatableComponents("example", manifest, emptyList())

        assertThat(rows.filter { Kind.AUTO_START in it.kinds }.map { it.displayName }).containsExactlyElementsIn(actions.dropLast(1)).inOrder()
    }

    private data class Expected(val name: String, val label: String, val kinds: Set<Kind>)
}

internal val classificationPermissions = listOf("android.permission.SCHEDULE_EXACT_ALARM")

private val launcher = ManifestIntentFilter(
    actions = listOf("android.intent.action.MAIN"),
    categories = listOf("android.intent.category.LAUNCHER"),
)
private val link = ManifestIntentFilter(
    actions = listOf("android.intent.action.VIEW"),
    categories = listOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"),
    data = listOf(IntentFilterData(scheme = "https")),
)
internal val classificationManifest = AndroidManifest(
    packageName = "example",
    application = ManifestApplication(
        activities = listOf(
            ManifestActivity(
                name = ".Share",
                label = "Share handler",
                exported = true,
                intentFilters = listOf(
                    ManifestIntentFilter(
                        actions = listOf("android.intent.action.SEND_MULTIPLE"),
                        categories = listOf("android.intent.category.DEFAULT"),
                        data = listOf(IntentFilterData(mimeType = "text/plain")),
                    ),
                ),
            ),
            ManifestActivity(name = ".LaunchFromLink", intentFilters = listOf(launcher, link)),
            ManifestActivity(name = ".LauncherOnly", intentFilters = listOf(launcher)),
            ManifestActivity(name = ".ViewOnly", intentFilters = listOf(link.copy(data = emptyList()))),
            ManifestActivity(name = ".ExplicitOnly", exported = true),
        ),
        services = listOf(ManifestService(name = ".MyPushService", foregroundServiceType = "dataSync")),
        receivers = listOf(
            ManifestReceiver(
                name = ".Boot",
                exported = true,
                permission = "example.PRIVATE",
                intentFilters = listOf(ManifestIntentFilter(actions = listOf("android.intent.action.BOOT_COMPLETED"))),
            ),
        ),
        providers = listOf(ManifestProvider(name = ".Provider", exported = true, grantUriPermissions = true)),
    ),
)
