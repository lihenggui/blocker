// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.device

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.ManifestApplication
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestPermission
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppManifestReaderTest {
    @Test fun givenBaseAndSplitApks_whenReading_thenOnlyMatchingSplitComponentsAreMerged() = runTest {
        val app = AppKey("example.app", UserId.SYSTEM)
        val basePath = "/data/app/example.app/base.apk"
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = app.packageName
                applicationInfo = ApplicationInfo().apply {
                    packageName = app.packageName
                    sourceDir = basePath
                    splitSourceDirs = arrayOf("/split/feature.apk", "/split/config.apk", "/split/unreadable.apk", "/split/other.apk")
                }
            },
        )
        val apps = FakeInstalledAppRepository().apply {
            sendApps(listOf(installedApp(app.packageName, app.userId).copy(apkPath = basePath)))
        }
        val reader = LocalAppManifestReader(apps, context, StandardTestDispatcher(testScheduler))
        val bootPermission = ManifestPermission("android.permission.RECEIVE_BOOT_COMPLETED")
        val boot = ManifestReceiver(".Boot", intentFilters = listOf(ManifestIntentFilter(actions = listOf("android.intent.action.BOOT_COMPLETED"))))
        val base = AndroidManifest(
            app.packageName,
            targetSdkVersion = 34,
            usesPermissions = listOf(bootPermission),
            application = ManifestApplication(receivers = listOf(boot)),
        )
        val feature = AndroidManifest(
            app.packageName,
            application = ManifestApplication(
                receivers = listOf(ManifestReceiver(".Push")),
                services = listOf(ManifestService(".Job")),
            ),
        )

        val merged = checkNotNull(
            reader.read(app) { file ->
                when (file.name) {
                    "base.apk" -> Result.success(base)
                    "feature.apk" -> Result.success(feature)
                    "config.apk" -> Result.success(feature.copy(application = ManifestApplication()))
                    "other.apk" -> Result.success(feature.copy(packageName = "other.app"))
                    else -> Result.failure(IOException("Unreadable split"))
                }
            },
        )

        assertThat(merged.targetSdkVersion).isEqualTo(34)
        assertThat(merged.usesPermissions).containsExactly(bootPermission)
        assertThat(merged.application.receivers.map { it.name }).containsExactly(".Boot", ".Push")
        assertThat(merged.application.services.map { it.name }).containsExactly(".Job")
    }
}
