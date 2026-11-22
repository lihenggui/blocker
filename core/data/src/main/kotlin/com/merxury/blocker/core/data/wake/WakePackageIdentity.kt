// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.merxury.blocker.core.extension.getVersionCode
import java.security.MessageDigest

@Suppress("DEPRECATION")
internal val wakeSigningFlags get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

internal val PackageInfo.wakeRevision get() = "${getVersionCode()}/$lastUpdateTime/$firstInstallTime"

@Suppress("DEPRECATION")
internal val PackageInfo.wakeSigners: String
    get() {
        val certs = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
        return certs.orEmpty().map { it.toByteArray().hexDigest() }.sorted().joinToString(",")
    }

internal fun ByteArray.hexDigest() = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
