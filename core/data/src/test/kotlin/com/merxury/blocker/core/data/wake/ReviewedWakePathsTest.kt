// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeIdentity
import com.merxury.blocker.core.database.wake.WakeRecord
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ReviewedWakePathsTest {
    @get:Rule val files = TemporaryFolder()

    private val pkg = "example.target"
    private val app = WakeIdentity(10122, pkg, "1/10/10", 1, "signed", "", 10, 100)
    private val event = WakeRecord("id", "boot", 12, 12, "statsd", "ServiceRequestEventReported", app.uid, app.uid, pkg, pkg, "$pkg/.Wake", null, 3, 1, pkg, "{}")
    private lateinit var apk: File
    private lateinit var catalog: ReviewedWakePaths

    @Suppress("DEPRECATION")
    @Before
    fun setUp() {
        apk = files.newFile()
        val context = mock<Context>()
        val assets = mock<AssetManager>()
        val pm = mock<PackageManager>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.packageManager).thenReturn(pm)
        whenever(pm.getApplicationInfo(pkg, 0)).thenReturn(ApplicationInfo().apply { sourceDir = apk.path })
        whenever(assets.list("reviewed-wake-paths")).thenReturn(arrayOf("test.json"))
        fun entry(actionJson: String) = """{"target":"$pkg","caller":"$pkg","component":"$pkg.Wake","action":$actionJson,"version":1,
            "signers":"signed","apks":["e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"],"review":"test"}"""
        val review = "[${entry("null")},${entry("\"sdk.peer.WAKE\"")}]"
        whenever(assets.open("reviewed-wake-paths/test.json")).thenReturn(review.byteInputStream())
        catalog = ReviewedWakePaths(context)
    }

    private fun candidates(
        observed: WakeRecord = event,
        identities: List<WakeIdentity> = listOf(app),
        profiles: List<Int> = listOf(0),
        resets: Map<String, Long> = emptyMap(),
    ) = catalog.evaluate(listOf(observed), identities, profiles, emptyList(), resets)

    @Test
    fun givenUnrecordedBindingAcrossReset_whenInspectedLater_thenOnlyUnresetTargetsQualify() {
        val snapshot = event.copy(source = "SERVICES", kind = "HeldConnection", action = "sdk.peer.WAKE", wallMillis = 30)

        assertThat(candidates(snapshot, resets = mapOf("" to 20L))).isEmpty()
        assertThat(candidates(snapshot, resets = mapOf(pkg to 20L))).isEmpty()
        assertThat(candidates(snapshot, resets = mapOf("other.target" to 20L))).hasSize(1)
    }

    @Test
    fun givenGlobalAndTargetResets_whenTimestampedRequestsArrive_thenOnlyRequestsAfterBothResetsQualify() {
        val request = event.copy(action = "sdk.peer.WAKE")
        for (resets in listOf(mapOf("" to 15L, pkg to 20L), mapOf("" to 20L, pkg to 15L))) {
            assertThat(candidates(request.copy(wallMillis = 16), resets = resets)).isEmpty()
            assertThat(candidates(request.copy(wallMillis = 20), resets = resets)).isEmpty()
            assertThat(candidates(request.copy(wallMillis = 21), resets = resets)).hasSize(1)
        }
    }

    @Test
    fun givenReviewedService_whenEvaluatingEvidence_thenOnlyVerifiedInstallationAndPathQualify() {
        val binding = event.copy(source = "SERVICES", kind = "HeldConnection", action = "sdk.peer.WAKE")
        assertThat(candidates(binding).single().action).isEqualTo("sdk.peer.WAKE")
        assertThat(candidates(binding.copy(action = "other.peer.WAKE"))).isEmpty()
        assertThat(candidates(binding.copy(source = "PROVIDERS"))).isEmpty()
        assertThat(candidates(binding.copy(action = null))).isEmpty()
        assertThat(candidates(event.copy(action = "WAKE"))).isEmpty()
        val match = candidates().single()
        for (scope in listOf("", pkg)) {
            assertThat(catalog.evaluate(listOf(event), listOf(app), listOf(0), emptyList(), mapOf(scope to 12L))).isEmpty()
        }
        assertThat(match.component).isEqualTo("$pkg.Wake")
        assertThat(candidates(event.copy(component = "$pkg/.Main"))).isEmpty()
        assertThat(candidates(event.copy(action = "user.action"))).isEmpty()
        assertThat(candidates(event.copy(kind = "am_proc_start"))).isEmpty()
        assertThat(candidates(event.copy(callerUid = 10123))).isEmpty()
        assertThat(candidates(event.copy(wallMillis = 9))).isEmpty()
        assertThat(candidates(identities = listOf(app.copy(signatures = "other")))).isEmpty()
        assertThat(candidates(identities = listOf(app, app.copy(packageName = "shared.uid")))).isEmpty()
        assertThat(candidates(profiles = listOf(0, 10))).isEmpty()
        assertThat(catalog.evaluate(listOf(event), listOf(app), listOf(0), listOf(match.copy(disabledByUser = true)))).isEmpty()
        apk.writeText("updated APK")
        assertThat(candidates()).isEmpty()
    }
}
