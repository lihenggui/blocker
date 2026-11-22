// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

/** Looks up the packages and signing certificates of an app calling the provider. */
internal interface CallerPackageSource {
    /** Packages sharing [uid], or null when the UID has no packages. */
    fun packagesForUid(uid: Int): List<String>?

    /**
     * SHA-256 digests (lowercase hex) of the certificates that currently sign [packageName],
     * or an empty set when the package or its signing info cannot be read.
     */
    fun signingCertificateSha256(packageName: String): Set<String>
}

/**
 * Decides whether the app behind a Binder calling UID may use [ComponentProvider].
 *
 * Blocker itself is always allowed. Any other caller must run as an allowlisted package
 * whose signing certificates are all pinned for that package.
 */
internal class ProviderCallerVerifier(
    private val ownUid: Int,
    private val trustedCallers: Map<String, Set<String>>,
    private val packageSource: CallerPackageSource,
) {
    fun isAllowed(callingUid: Int): Boolean {
        if (callingUid == ownUid) return true
        val packages = packageSource.packagesForUid(callingUid) ?: return false
        return packages.any(::isTrustedPackage)
    }

    private fun isTrustedPackage(packageName: String): Boolean {
        val pinnedDigests = trustedCallers[packageName] ?: return false
        val signerDigests = packageSource.signingCertificateSha256(packageName)
        return signerDigests.isNotEmpty() && pinnedDigests.containsAll(signerDigests)
    }
}
