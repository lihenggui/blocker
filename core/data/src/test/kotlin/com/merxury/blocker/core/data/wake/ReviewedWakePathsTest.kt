// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.WakeIdentity
import com.merxury.blocker.core.model.wake.WakeProtection
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    private val peer = app.copy(uid = app.uid + 1, packageName = "example.peer")
    private val clone = app.copy(uid = 999 * UIDS_PER_USER + app.uid, revision = "profile/1/path")
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
        val cache = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("wake_reviewed_sdk", Context.MODE_PRIVATE)).thenReturn(cache)
        for (identity in listOf(app, peer)) {
            whenever(cache.getString("${identity.packageName}/Lsdk/", null)).thenReturn("${identity.revision}/${identity.signatures}/fingerprint/y")
        }
        whenever(context.assets).thenReturn(assets)
        whenever(context.packageManager).thenReturn(pm)
        whenever(pm.getApplicationInfo(pkg, 0)).thenReturn(ApplicationInfo().apply { sourceDir = apk.path })
        whenever(assets.list("reviewed-wake-paths")).thenReturn(arrayOf("test.json"))
        fun entry(actionJson: String, proof: String = """"caller":"$pkg"""") = """{"target":"$pkg",$proof,"component":"$pkg.Wake","action":$actionJson,"version":1,
            "signers":"signed","apks":["e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"],"review":"test"}"""
        val peerSdk = """{"component":"$pkg.Wake","action":"sdk.CROSS_APP","peerSdk":true,
            "sdkNamespace":"Lsdk/","sdkFingerprint":"fingerprint","review":"sdk-test"}"""
        val reviews = listOf(
            entry("null"),
            entry(""""sdk.peer.WAKE""""),
            entry(""""sdk.fixedPeer.WAKE"""", """"caller":"${peer.packageName}""""),
            entry(""""dedicated.WAKE"""", """"method":"PM","dedicated":true"""),
            peerSdk,
        )
        val review = reviews.joinToString(",", "[", "]")
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
    fun givenOwnerOnlyAppAndUnrelatedWorkApp_whenEvaluating_thenOwnerIfwQualifiesAndIsRetained() {
        val unrelated = app.copy(uid = 10 * UIDS_PER_USER + app.uid, packageName = "other.work", versionCode = 100)
        val identities = listOf(app, unrelated)
        val profiles = listOf(999, 0, 10)
        val match = candidates(identities = identities, profiles = profiles).single()
        assertThat(match.targetRevision).isEqualTo(app.revision)
        assertThat(match.callerRevision).isEqualTo(app.revision)
        assertThat(catalog.evaluate(emptyList(), identities, profiles, listOf(match))).containsExactly(match)
    }

    @Test
    fun givenTargetAlsoInstalledInSecondaryProfile_whenEvaluating_thenOnlyOwnerPmQualifies() {
        val pm = event.copy(action = "dedicated.WAKE")
        val previous = listOf(candidates().single(), candidates(pm).single())
        for (version in listOf(-1L, app.versionCode, app.versionCode + 1)) {
            val identities = listOf(app, clone.copy(versionCode = version))
            val profiles = listOf(0, 999)
            assertThat(candidates(identities = identities, profiles = profiles)).isEmpty()
            assertThat(candidates(pm, identities, profiles).single().method).isEqualTo("PM")
            assertThat(catalog.evaluate(emptyList(), identities, profiles, previous).map(WakeProtection::method)).containsExactly("PM")
        }
    }

    @Test
    fun givenSecondaryPeerInstalls_whenEvaluating_thenNeitherFixedNorSdkIfwQualifies() {
        val fixed = event.copy(callerUid = peer.uid, callerPackage = peer.packageName, action = "sdk.fixedPeer.WAKE")
        val sdk = fixed.copy(action = "sdk.CROSS_APP")
        val profiles = listOf(0, 10)
        val owner = listOf(app, peer)
        val matches = listOf(candidates(fixed, owner, profiles).single(), candidates(sdk, owner, profiles).single())
        assertThat(matches.map(WakeProtection::callerRevision)).containsExactly(peer.revision, peer.revision)
        assertThat(catalog.evaluate(emptyList(), owner, profiles, matches)).containsExactlyElementsIn(matches)
        val peerCopy = peer.copy(uid = 10 * UIDS_PER_USER + peer.uid, revision = "profile/1/peer")
        for (version in listOf(-1L, peer.versionCode, peer.versionCode + 1)) {
            val identities = owner + peerCopy.copy(versionCode = version)
            assertThat(catalog.evaluate(listOf(fixed, sdk), identities, profiles, matches)).isEmpty()
        }
    }

    @Test
    fun givenOwnerMissingFromProfileScope_whenEvaluating_thenNoMethodQualifies() {
        val requests = listOf(event, event.copy(action = "dedicated.WAKE"))
        val previous = catalog.evaluate(requests, listOf(app), listOf(0), emptyList())
        assertThat(previous.map(WakeProtection::method)).containsExactly("IFW", "PM")
        assertThat(catalog.evaluate(requests, listOf(app, clone), listOf(999), previous)).isEmpty()
    }

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
    fun givenRemovedReviewAndIncompleteIdentities_whenReconciling_thenOnlyTheRemovedReblockEnds() = runTest {
        val pending = candidates().single().copy(pendingChange = true)
        val retired = pending.copy(pathId = "retired", component = "$pkg.Retired")
        val repository = mock<WakeRepository>()
        whenever(repository.protections()).thenReturn(listOf(pending, retired))
        whenever(repository.observedComponents(any())).thenReturn(emptyList())
        whenever(repository.firewallResets()).thenReturn(emptyMap())
        val protection = LocalWakeProtectionRepository(
            repository, mock(), catalog, mock(), mock(), FakePrivilegedChannel(), mock(), mock(), WakeRuleLock(), mock(),
        )

        protection.applyReviewedPaths(WakeImport("boot", emptyList(), listOf(UserId.SYSTEM.value)), WakeWrites.Enforce, event.wallMillis)

        verify(repository).saveProtections(listOf(retired.copy(pendingChange = false)))
    }

    @Test
    fun givenAllowedPaths_whenCallerOrTargetRevisionsChange_thenFreshEvidenceStillRespectsAllow() {
        val ifw = event.copy(callerUid = peer.uid, callerPackage = peer.packageName, action = "sdk.fixedPeer.WAKE")
        val pm = event.copy(action = "dedicated.WAKE")
        val owner = listOf(app, peer)
        val previous = listOf(candidates(ifw, owner).single(), candidates(pm, owner).single()).map {
            it.copy(disabledByUser = true, callerRevision = "previous-caller", targetRevision = "previous-target")
        }

        assertThat(catalog.evaluate(listOf(ifw, pm), owner, listOf(UserId.SYSTEM.value), previous)).isEmpty()
    }

    @Test
    fun givenPendingReblocksFromOldRevisions_whenFreshEvidenceQualifies_thenRequestsUseCurrentRevisions() {
        val ifw = event.copy(callerUid = peer.uid, callerPackage = peer.packageName, action = "sdk.fixedPeer.WAKE")
        val pm = event.copy(action = "dedicated.WAKE")
        val owner = listOf(app, peer)
        val fresh = listOf(candidates(ifw, owner).single(), candidates(pm, owner).single())
        val previous = fresh.map { it.copy(callerRevision = "previous-caller", targetRevision = "previous-target", pendingChange = true) }

        assertThat(catalog.evaluate(listOf(ifw, pm), owner, listOf(UserId.SYSTEM.value), previous))
            .containsExactlyElementsIn(fresh.map { it.copy(pendingChange = true) })
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
        assertThat(catalog.evaluate(listOf(event), listOf(app), listOf(0), listOf(match.copy(disabledByUser = true)))).isEmpty()
        apk.writeText("updated APK")
        assertThat(candidates()).isEmpty()
    }
}
