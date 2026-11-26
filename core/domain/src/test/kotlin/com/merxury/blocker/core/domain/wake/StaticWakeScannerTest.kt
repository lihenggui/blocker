// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.ManifestActivity
import com.merxury.blocker.core.model.manifest.ManifestApplication
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestProvider
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import org.junit.Test

class StaticWakeScannerTest {
    @Test fun givenConnectivityReceivers_whenScanning_thenOnlyLegacyTargetsMatch() {
        val connectivity = "android.net.conn.CONNECTIVITY_CHANGE"
        val manifest = AndroidManifest(
            "example.app",
            application = ManifestApplication(
                receivers = listOf(ManifestReceiver(".NetworkReceiver", intentFilters = listOf(ManifestIntentFilter(actions = listOf(connectivity))))),
            ),
        )
        assertThat(StaticWakeScanner.scan(manifest.copy(targetSdkVersion = 23)).single().action).isEqualTo(connectivity)
        assertThat(StaticWakeScanner.scan(manifest.copy(targetSdkVersion = 24))).isEmpty()
        assertThat(StaticWakeScanner.scan(manifest.copy(targetSdkVersion = 35))).isEmpty()
        assertThat(StaticWakeScanner.scan(manifest)).isEmpty()
    }

    @Test fun givenAllFiveCategories_whenScanning_thenQualifiesNamesAndSortsByCategoryAndClass() {
        val manifest = manifest(
            receivers = listOf(
                ManifestReceiver("UserPresent", intentFilters = filters("android.intent.action.USER_PRESENT")),
                ManifestReceiver(".ZuluBoot", intentFilters = filters("android.intent.action.LOCKED_BOOT_COMPLETED")),
                ManifestReceiver(".BootReceiver", enabled = false, intentFilters = filters("android.intent.action.BOOT_COMPLETED")),
            ),
            services = listOf(
                ManifestService(".Sync", intentFilters = filters("android.content.SyncAdapter")),
                ManifestService(".Authenticator", intentFilters = filters("android.accounts.AccountAuthenticator")),
                ManifestService("vendor.sdk.PushService", intentFilters = filters("com.google.firebase.MESSAGING_EVENT")),
                ManifestService(".Job", permission = "android.permission.BIND_JOB_SERVICE"),
            ),
        )

        assertThat(StaticWakeScanner.scan(manifest)).containsExactly(
            StaticWakeDeclaration("pkg", "pkg.BootReceiver", ComponentType.RECEIVER, PotentialWakeCategory.BOOT_AUTOSTART, "android.intent.action.BOOT_COMPLETED", false),
            StaticWakeDeclaration("pkg", "pkg.ZuluBoot", ComponentType.RECEIVER, PotentialWakeCategory.BOOT_AUTOSTART, "android.intent.action.LOCKED_BOOT_COMPLETED", true),
            StaticWakeDeclaration("pkg", "vendor.sdk.PushService", ComponentType.SERVICE, PotentialWakeCategory.PUSH_RECEIVER, "com.google.firebase.MESSAGING_EVENT", true),
            StaticWakeDeclaration("pkg", "pkg.Job", ComponentType.SERVICE, PotentialWakeCategory.SCHEDULED_JOB, null, true),
            StaticWakeDeclaration("pkg", "pkg.Authenticator", ComponentType.SERVICE, PotentialWakeCategory.ACCOUNT_SYNC, "android.accounts.AccountAuthenticator", true),
            StaticWakeDeclaration("pkg", "pkg.Sync", ComponentType.SERVICE, PotentialWakeCategory.ACCOUNT_SYNC, "android.content.SyncAdapter", true),
            StaticWakeDeclaration("pkg", "pkg.UserPresent", ComponentType.RECEIVER, PotentialWakeCategory.SYSTEM_BROADCAST, "android.intent.action.USER_PRESENT", true),
        ).inOrder()
    }

    @Test fun givenComponentsMatchMultipleCategories_whenScanning_thenHighestPriorityWinsAcrossFiltersAndNames() {
        val manifest = manifest(
            receivers = listOf(
                ManifestReceiver("pkg.Boot", intentFilters = filters("android.intent.action.MY_PACKAGE_REPLACED")),
                ManifestReceiver(
                    ".Boot",
                    intentFilters = filters("android.intent.action.MY_PACKAGE_REPLACED", "com.google.android.c2dm.intent.RECEIVE") +
                        filters("android.intent.action.BOOT_COMPLETED"),
                ),
                ManifestReceiver(".Push", intentFilters = filters("android.intent.action.USER_PRESENT", "com.xiaomi.push.PING_TIMER")),
            ),
            services = listOf(
                ManifestService(".Job", permission = "android.permission.BIND_JOB_SERVICE", intentFilters = filters("android.content.SyncAdapter")),
                ManifestService(
                    ".PushJob",
                    permission = "android.permission.BIND_JOB_SERVICE",
                    intentFilters = filters("android.content.SyncAdapter", "com.google.firebase.MESSAGING_EVENT"),
                ),
            ),
        )

        assertThat(StaticWakeScanner.scan(manifest).map { it.component to it.category }).containsExactly(
            "pkg.Boot" to PotentialWakeCategory.BOOT_AUTOSTART,
            "pkg.Push" to PotentialWakeCategory.PUSH_RECEIVER,
            "pkg.PushJob" to PotentialWakeCategory.PUSH_RECEIVER,
            "pkg.Job" to PotentialWakeCategory.SCHEDULED_JOB,
        ).inOrder()
    }

    @Test fun givenSupportedPushActions_whenScanningReceiversAndServices_thenEachMatchesExactly() {
        val actions = listOf(
            "com.google.android.c2dm.intent.RECEIVE",
            "com.google.firebase.MESSAGING_EVENT",
            "com.xiaomi.mipush.RECEIVE_MESSAGE",
            "com.xiaomi.mipush.MESSAGE_ARRIVED",
            "com.xiaomi.push.PING_TIMER",
            "com.huawei.push.action.MESSAGING_EVENT",
            "com.huawei.android.push.intent.RECEIVE",
            "com.huawei.intent.action.PUSH",
            "com.heytap.mcs.action.RECEIVE_MCS_MESSAGE",
            "com.coloros.mcs.action.RECEIVE_MCS_MESSAGE",
            "com.vivo.pushclient.action.RECEIVE",
            "com.meizu.flyme.push.intent.MESSAGE",
        )
        val manifest = manifest(
            receivers = actions.mapIndexed { index, action -> ManifestReceiver(".Receiver$index", intentFilters = filters(action)) },
            services = actions.mapIndexed { index, action -> ManifestService(".Service$index", intentFilters = filters(action)) },
        )

        val entries = StaticWakeScanner.scan(manifest)

        assertThat(entries.map { it.category }.distinct()).containsExactly(PotentialWakeCategory.PUSH_RECEIVER)
        assertThat(entries.filter { it.componentType == ComponentType.RECEIVER }.map { it.action }).containsExactlyElementsIn(actions)
        assertThat(entries.filter { it.componentType == ComponentType.SERVICE }.map { it.action }).containsExactlyElementsIn(actions)
    }

    @Test fun givenWidgetUiAndUnrelatedDeclarations_whenScanning_thenNoEntryIsReturned() {
        val manifest = manifest(
            receivers = listOf(
                ManifestReceiver(".Widget", intentFilters = filters("android.appwidget.action.APPWIDGET_UPDATE")),
                ManifestReceiver(".Custom", intentFilters = filters("pkg.android.intent.action.BOOT_COMPLETED", "com.xiaomi.push.PING_TIMER_EXTRA")),
                ManifestReceiver(".SyncReceiver", intentFilters = filters("android.content.SyncAdapter")),
            ),
            services = listOf(
                ManifestService(".JobByName"),
                ManifestService(".NearMatch", permission = "pkg.android.permission.BIND_JOB_SERVICE"),
                ManifestService(".BootService", intentFilters = filters("android.intent.action.BOOT_COMPLETED", "android.intent.action.USER_PRESENT")),
            ),
        ).let {
            it.copy(
                application = it.application.copy(
                    activities = listOf(
                        ManifestActivity(
                            ".Main",
                            intentFilters = listOf(ManifestIntentFilter(actions = listOf("android.intent.action.MAIN"), categories = listOf("android.intent.category.LAUNCHER"))),
                        ),
                        ManifestActivity(".PushActivity", intentFilters = filters("com.google.firebase.MESSAGING_EVENT")),
                    ),
                    providers = listOf(ManifestProvider(".Provider", permission = "android.permission.BIND_JOB_SERVICE")),
                ),
            )
        }

        assertThat(StaticWakeScanner.scan(manifest)).isEmpty()
    }

    private fun manifest(
        receivers: List<ManifestReceiver> = emptyList(),
        services: List<ManifestService> = emptyList(),
    ) = AndroidManifest("pkg", application = ManifestApplication(receivers = receivers, services = services))

    private fun filters(vararg actions: String) = listOf(ManifestIntentFilter(actions = actions.toList()))
}
