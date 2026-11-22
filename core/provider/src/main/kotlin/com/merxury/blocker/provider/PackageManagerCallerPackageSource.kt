// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.provider

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.merxury.blocker.core.extension.getPackageInfoCompat
import java.security.MessageDigest

/** [CallerPackageSource] backed by the system [PackageManager]. */
internal class PackageManagerCallerPackageSource(
    private val packageManager: PackageManager,
) : CallerPackageSource {
    override fun packagesForUid(uid: Int): List<String>? = packageManager.getPackagesForUid(uid)?.toList()

    override fun signingCertificateSha256(packageName: String): Set<String> = currentSigners(packageName)
        .mapTo(mutableSetOf()) { it.toByteArray().sha256Hex() }

    @Suppress("DEPRECATION")
    private fun currentSigners(packageName: String): Array<out Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfoCompat(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            ?.signingInfo
            ?.apkContentsSigners
    } else {
        packageManager.getPackageInfoCompat(packageName, PackageManager.GET_SIGNATURES)?.signatures
    }.orEmpty()
}

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
