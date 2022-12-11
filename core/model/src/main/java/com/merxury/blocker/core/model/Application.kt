/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.model

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import kotlinx.datetime.Instant
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Created by Mercury on 2017/12/30.
 * An entity class that describe simplified application information
 */
@Parcelize
data class Application(
    var packageName: String = "",
    var versionName: String? = "",
    var isEnabled: Boolean = false,
    var label: String = "",
    var firstInstallTime: @RawValue Instant? = null,
    var lastUpdateTime: @RawValue Instant? = null,
    var packageInfo: PackageInfo? = null,
) : Parcelable {
    // TODO customized parceler should be removed
    private companion object : Parceler<Application> {
        override fun create(parcel: Parcel): Application {
            return Application(
                packageName = parcel.readString().orEmpty(),
                versionName = parcel.readString(),
                isEnabled = parcel.readInt() == 1,
                label = parcel.readString().orEmpty(),
                firstInstallTime = Instant.fromEpochMilliseconds(parcel.readLong()),
                lastUpdateTime = Instant.fromEpochMilliseconds(parcel.readLong()),
                packageInfo = parcel.readParcelableCompat(PackageInfo::class.java.classLoader)
            )
        }

        override fun Application.write(parcel: Parcel, flags: Int) {
            parcel.writeString(packageName)
            parcel.writeString(versionName)
            parcel.writeByte(if (isEnabled) 1 else 0)
            parcel.writeString(label)
            parcel.writeLong(firstInstallTime?.toEpochMilliseconds() ?: 0)
            parcel.writeLong(lastUpdateTime?.toEpochMilliseconds() ?: 0)
            parcel.writeParcelable(packageInfo, flags)
        }
    }
}

fun PackageInfo.toApplication(pm: PackageManager): Application {
    return Application(
        packageName = packageName,
        versionName = versionName,
        isEnabled = applicationInfo?.enabled ?: false,
        label = applicationInfo?.loadLabel(pm).toString(),
        firstInstallTime = Instant.fromEpochMilliseconds(firstInstallTime),
        lastUpdateTime = Instant.fromEpochMilliseconds(lastUpdateTime),
        packageInfo = this,
    )
}

inline fun <reified T : Parcelable> Parcel.readParcelableCompat(classLoader: ClassLoader?): T? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> readParcelable(
            classLoader,
            T::class.java
        )

        else -> @Suppress("DEPRECATION") readParcelable(classLoader)
    }
