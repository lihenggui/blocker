// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderCallerVerifierTest {

    @Test
    fun givenOwnUid_whenVerify_thenAllowed() {
        val verifier = verifier(FakeCallerPackageSource())

        assertThat(verifier.isAllowed(OWN_UID)).isTrue()
    }

    @Test
    fun givenLibCheckerSignedWithReleaseCertificate_whenVerify_thenAllowed() {
        val source = FakeCallerPackageSource(
            packagesByUid = mapOf(CALLER_UID to listOf(LIBCHECKER)),
            signersByPackage = mapOf(LIBCHECKER to setOf(LIBCHECKER_RELEASE_SHA256)),
        )

        assertThat(verifier(source).isAllowed(CALLER_UID)).isTrue()
    }

    @Test
    fun givenLibCheckerSignedWithFdroidCertificate_whenVerify_thenAllowed() {
        val source = FakeCallerPackageSource(
            packagesByUid = mapOf(CALLER_UID to listOf(LIBCHECKER)),
            signersByPackage = mapOf(LIBCHECKER to setOf(LIBCHECKER_FDROID_SHA256)),
        )

        assertThat(verifier(source).isAllowed(CALLER_UID)).isTrue()
    }

    @Test
    fun givenLibCheckerNameWithWrongCertificate_whenVerify_thenDenied() {
        val source = FakeCallerPackageSource(
            packagesByUid = mapOf(CALLER_UID to listOf(LIBCHECKER)),
            signersByPackage = mapOf(LIBCHECKER to setOf(OTHER_SHA256)),
        )

        assertThat(verifier(source).isAllowed(CALLER_UID)).isFalse()
    }

    @Test
    fun givenLibCheckerWithPinnedAndUnpinnedSigners_whenVerify_thenDenied() {
        val source = FakeCallerPackageSource(
            packagesByUid = mapOf(CALLER_UID to listOf(LIBCHECKER)),
            signersByPackage = mapOf(LIBCHECKER to setOf(LIBCHECKER_RELEASE_SHA256, OTHER_SHA256)),
        )

        assertThat(verifier(source).isAllowed(CALLER_UID)).isFalse()
    }

    @Test
    fun givenLibCheckerWithoutSigningInfo_whenVerify_thenDenied() {
        val source = FakeCallerPackageSource(
            packagesByUid = mapOf(CALLER_UID to listOf(LIBCHECKER)),
        )

        assertThat(verifier(source).isAllowed(CALLER_UID)).isFalse()
    }

    @Test
    fun givenUnknownPackage_whenVerify_thenDenied() {
        val source = FakeCallerPackageSource(
            packagesByUid = mapOf(CALLER_UID to listOf("com.example.unknown")),
            signersByPackage = mapOf("com.example.unknown" to setOf(LIBCHECKER_RELEASE_SHA256)),
        )

        assertThat(verifier(source).isAllowed(CALLER_UID)).isFalse()
    }

    @Test
    fun givenNullCallerPackages_whenVerify_thenDenied() {
        val source = FakeCallerPackageSource(packagesByUid = emptyMap())

        assertThat(verifier(source).isAllowed(CALLER_UID)).isFalse()
    }

    private fun verifier(source: CallerPackageSource) = ProviderCallerVerifier(
        ownUid = OWN_UID,
        trustedCallers = TRUSTED_PROVIDER_CALLERS,
        packageSource = source,
    )

    private class FakeCallerPackageSource(
        private val packagesByUid: Map<Int, List<String>> = emptyMap(),
        private val signersByPackage: Map<String, Set<String>> = emptyMap(),
    ) : CallerPackageSource {
        override fun packagesForUid(uid: Int): List<String>? = packagesByUid[uid]

        override fun signingCertificateSha256(packageName: String): Set<String> = signersByPackage[packageName].orEmpty()
    }

    private companion object {
        const val OWN_UID = 10_100
        const val CALLER_UID = 10_200
        const val LIBCHECKER = "com.absinthe.libchecker"
        const val LIBCHECKER_RELEASE_SHA256 = "b12e1cfbfe285849d6740018022eec2209feb28c2685df6f4d2f97dbde3b5775"
        const val LIBCHECKER_FDROID_SHA256 = "47274114ab10ade16e41088a991be3ea4a621855f2487f1edc62187367f4a5c0"
        const val OTHER_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
