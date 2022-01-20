package com.merxury.libkit.entity

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.merxury.libkit.utils.ApkUtils.getMinSdkVersion
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

/**
 * Created by Mercury on 2017/12/30.
 * An entity class that describe simplified application information
 */
class Application : Parcelable {
    var packageName: String = ""
    var versionName: String = ""
    var versionCode = 0
    var isEnabled = false
    var isBlocked = false
    var targetSdkVersion = 0
    var minSdkVersion = 0
    var nonLocalizedLabel: String? = null
    var sourceDir: String? = null
    var publicSourceDir: String? = null
    var dataDir: String? = null
    var label: String = ""
    var firstInstallTime: Date? = null
    var lastUpdateTime: Date? = null

    private constructor() {}
    constructor(info: PackageInfo) {
        packageName = info.packageName
        versionName = info.versionName
        versionCode = info.versionCode
        val appDetails = info.applicationInfo
        if (appDetails != null) {
            targetSdkVersion = appDetails.targetSdkVersion
            isEnabled = appDetails.enabled
        }
    }

    constructor(pm: PackageManager, info: PackageInfo) : this(info) {
        val appDetail = info.applicationInfo
        targetSdkVersion = appDetail.targetSdkVersion
        nonLocalizedLabel = appDetail.nonLocalizedLabel?.toString()
        sourceDir = appDetail.sourceDir
        publicSourceDir = appDetail.sourceDir
        dataDir = appDetail.dataDir
        label = appDetail.loadLabel(pm).toString()
        val baseApkPath = File(publicSourceDir)
        minSdkVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appDetail.minSdkVersion
        } else {
            // TODO Will remove this blocking call
            runBlocking {
                getMinSdkVersion(baseApkPath)
            }
        }
        firstInstallTime = Date(info.firstInstallTime)
        lastUpdateTime = Date(info.lastUpdateTime)
    }

    constructor(pm: PackageManager, info: PackageInfo, blocked: Boolean) : this(pm, info) {
        isBlocked = blocked
    }

    protected constructor(`in`: Parcel) {
        label = `in`.readString() ?: ""
        packageName = `in`.readString() ?: ""
        versionName = `in`.readString() ?: ""
        versionCode = `in`.readInt()
        isEnabled = `in`.readByte().toInt() != 0
        isBlocked = `in`.readByte().toInt() != 0
        minSdkVersion = `in`.readInt()
        targetSdkVersion = `in`.readInt()
        nonLocalizedLabel = `in`.readString()
        sourceDir = `in`.readString()
        publicSourceDir = `in`.readString()
        dataDir = `in`.readString()
        firstInstallTime = Date(`in`.readLong())
        lastUpdateTime = Date(`in`.readLong())
    }

    override fun describeContents(): Int {
        return 0
    }

    fun getApplicationIcon(pm: PackageManager): Drawable? {
        try {
            return pm.getApplicationIcon(packageName!!)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    override fun toString(): String {
        return "Application{" +
                "label='" + label + '\'' +
                ", packageName='" + packageName + '\'' +
                ", versionName='" + versionName + '\'' +
                ", versionCode=" + versionCode +
                ", enabled=" + isEnabled +
                ", blocked=" + isBlocked +
                ", minSdkVersion=" + minSdkVersion +
                ", targetSdkVersion=" + targetSdkVersion +
                ", nonLocalizedLabel='" + nonLocalizedLabel + '\'' +
                ", sourceDir='" + sourceDir + '\'' +
                ", publicSourceDir='" + publicSourceDir + '\'' +
                ", dataDir='" + dataDir + '\'' +
                '}'
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(label)
        dest.writeString(packageName)
        dest.writeString(versionName)
        dest.writeInt(versionCode)
        dest.writeByte(if (isEnabled) 1.toByte() else 0.toByte())
        dest.writeByte(if (isBlocked) 1.toByte() else 0.toByte())
        dest.writeInt(minSdkVersion)
        dest.writeInt(targetSdkVersion)
        dest.writeString(nonLocalizedLabel)
        dest.writeString(sourceDir)
        dest.writeString(publicSourceDir)
        dest.writeString(dataDir)
        dest.writeLong(firstInstallTime!!.time)
        dest.writeLong(lastUpdateTime!!.time)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Application> = object : Parcelable.Creator<Application> {
            override fun createFromParcel(source: Parcel): Application {
                return Application(source)
            }

            override fun newArray(size: Int): Array<Application?> {
                return arrayOfNulls(size)
            }
        }
    }
}