package com.merxury.libkit.utils

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException

object ApkUtils {
    private val TAG = "APKUtils"

    /**
     * Get [AssetManager] using reflection
     *
     * @return
     */
    private val assetManager: Any?
        get() {
            val assetManagerClass: Class<*>
            try {
                assetManagerClass = Class
                        .forName("android.content.res.AssetManager")
                return assetManagerClass.newInstance()
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            } catch (e: InstantiationException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }

            return null
        }

    /**
     * Parses AndroidManifest of the given apkFile and returns the value of
     * minSdkVersion using undocumented API which is marked as
     * "not to be used by applications"
     * Source: https://stackoverflow.com/questions/20372193/get-minsdkversion-and-targetsdkversion-from-apk-file
     *
     * @param apkFile
     * @return minSdkVersion or -1 if not found in Manifest
     */
    fun getMinSdkVersion(apkFile: File): Int {
        try {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses-sdk") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "minSdkVersion") {
                            return parser.getAttributeIntValue(i, -1)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
        }
        return -1
    }

    fun getActivities(pm: PackageManager, packageName: String): List<ActivityInfo> {
        val activities = mutableListOf<ActivityInfo>()
        try {
            val packageInfo = pm.getPackageInfo(packageName, 0)
            val parser = getParserForManifest(File(packageInfo.applicationInfo.sourceDir))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "activity") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "name") {
                            val componentInfo = ActivityInfo()
                            componentInfo.packageName = packageName
                            componentInfo.name = parser.getAttributeValue(i)
                            componentInfo.enabled = ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(componentInfo.packageName, componentInfo.name))
                            activities.add(componentInfo)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
        }
        return activities
    }

    fun getPackageName(apkFile: File): String {
        var packageName: String? = null
        try {
            val parser = getParserForManifest(apkFile)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "manifest") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "package") {
                            return parser.getAttributeValue(i)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, e.message)
        }
        return ""
    }


    /**
     * Tries to get the parser for the given apkFile from [AssetManager]
     * using undocumented API which is marked as
     * "not to be used by applications"
     *
     * @param apkFile
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun getParserForManifest(apkFile: File): XmlResourceParser {
        val assetManagerInstance = assetManager
        val cookie = addAssets(apkFile, assetManagerInstance!!)
        return (assetManagerInstance as AssetManager).openXmlResourceParser(
                cookie, "AndroidManifest.xml")
    }

    /**
     * Get the cookie of an asset using an undocumented API call that is marked
     * as "no to be used by applications" in its source code
     *
     * @return the cookie
     * @see [AssetManager.java:612](http://androidxref.com/5.1.1_r6/xref/frameworks/base/core/java/android/content/res/AssetManager.java.612)
     */
    private fun addAssets(apkFile: File, assetManagerInstance: Any): Int {
        try {
            val addAssetPath = assetManagerInstance.javaClass.getMethod(
                    "addAssetPath", String::class.java)
            return addAssetPath.invoke(assetManagerInstance,
                    apkFile.absolutePath) as Int
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        return -1
    }
}
