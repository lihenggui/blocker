package com.merxury.blocker.core.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable

inline fun <reified T : Parcelable> Parcel.readParcelableCompat(classLoader: ClassLoader?): T? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> readParcelable(
            classLoader,
            T::class.java
        )

        else -> @Suppress("DEPRECATION") readParcelable(classLoader)
    }

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))

    else -> @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
}

fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int): ApplicationInfo =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))

        else -> @Suppress("DEPRECATION") getApplicationInfo(packageName, flags)
    }

fun PackageManager.getInstalledPackagesCompat(flags: Int): List<PackageInfo> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
    else -> @Suppress("DEPRECATION") getInstalledPackages(flags)
}
