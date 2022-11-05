package com.merxury.libkit.entity

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.merxury.libkit.utils.ApkUtils.getMinSdkVersion
import com.merxury.libkit.utils.readParcelableCompat
import java.io.File
import java.util.*

/**
 * Created by Mercury on 2017/12/30.
 * An entity class that describe simplified application information
 */
class Application() : Parcelable {
    var packageName: String = ""
    var versionName: String? = ""
    var isEnabled = false
    var label: String = ""
    var firstInstallTime: Date? = null
    var lastUpdateTime: Date? = null
    var packageInfo: PackageInfo? = null

    constructor(parcel: Parcel) : this() {
        packageName = parcel.readString().orEmpty()
        versionName = parcel.readString()
        isEnabled = parcel.readByte() != 0.toByte()
        label = parcel.readString().orEmpty()
        packageInfo = parcel.readParcelableCompat(PackageInfo::class.java.classLoader)
        firstInstallTime = Date(parcel.readLong())
        lastUpdateTime = Date(parcel.readLong())
    }

    constructor(pm: PackageManager, info: PackageInfo) : this() {
        packageInfo = info
        packageName = info.packageName
        versionName = info.versionName
        label = getLabel(pm)
        val appDetails = info.applicationInfo
        if (appDetails != null) {
            isEnabled = appDetails.enabled
        }
        firstInstallTime = Date(info.firstInstallTime)
        lastUpdateTime = Date(info.lastUpdateTime)
    }

    suspend fun getMinSdkVersion(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return packageInfo?.applicationInfo?.minSdkVersion ?: 0
        } else {
            val publicSourceDir = packageInfo?.applicationInfo?.publicSourceDir ?: return 0
            return getMinSdkVersion(File(publicSourceDir))
        }
    }

    fun getLabel(pm: PackageManager): String {
        return packageInfo?.applicationInfo?.loadLabel(pm)?.toString() ?: ""
    }

    override fun toString(): String {
        return "Application(packageName='$packageName', versionName='$versionName', isEnabled=$isEnabled, label='$label', firstInstallTime=$firstInstallTime, lastUpdateTime=$lastUpdateTime, packageInfo=$packageInfo)"
    }


    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeString(versionName)
        parcel.writeByte(if (isEnabled) 1 else 0)
        parcel.writeString(label)
        parcel.writeParcelable(packageInfo, flags)
        parcel.writeLong(firstInstallTime?.time ?: 0)
        parcel.writeLong(lastUpdateTime?.time ?: 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Application

        if (packageName != other.packageName) return false
        if (versionName != other.versionName) return false
        if (isEnabled != other.isEnabled) return false
        if (label != other.label) return false
        if (firstInstallTime != other.firstInstallTime) return false
        if (lastUpdateTime != other.lastUpdateTime) return false
        if (packageInfo != other.packageInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + (versionName?.hashCode() ?: 0)
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + (firstInstallTime?.hashCode() ?: 0)
        result = 31 * result + (lastUpdateTime?.hashCode() ?: 0)
        result = 31 * result + (packageInfo?.hashCode() ?: 0)
        return result
    }

    companion object CREATOR : Parcelable.Creator<Application> {
        override fun createFromParcel(parcel: Parcel): Application {
            return Application(parcel)
        }

        override fun newArray(size: Int): Array<Application?> {
            return arrayOfNulls(size)
        }
    }
}