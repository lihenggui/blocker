package com.merxury.libkit.utils

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import com.elvishew.xlog.XLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException

object ApkUtils {
    private val logger = XLog.tag("ApkUtils").build()

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
                logger.e("Cannot create AssetManager", e)
            } catch (e: InstantiationException) {
                logger.e("Cannot create AssetManager", e)
            } catch (e: IllegalAccessException) {
                logger.e("Cannot create AssetManager", e)
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
    suspend fun getMinSdkVersion(
        apkFile: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Int {
        return withContext(dispatcher) {
            try {
                val parser = getParserForManifest(apkFile)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses-sdk") {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i) == "minSdkVersion") {
                                return@withContext parser.getAttributeIntValue(i, -1)
                            }
                        }
                    }
                }
            } catch (e: XmlPullParserException) {
                logger.e("Cannot parse manifest", e)
            } catch (e: IOException) {
                logger.e("Cannot parse manifest", e)
            }
            return@withContext -1
        }
    }

    suspend fun getActivities(pm: PackageManager, packageName: String): MutableList<ActivityInfo> {
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
                            componentInfo.enabled = ApplicationUtil.checkComponentIsEnabled(
                                pm,
                                ComponentName(componentInfo.packageName, componentInfo.name)
                            )
                            activities.add(componentInfo)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            logger.e("Cannot parse Activities from xml", e)
        } catch (e: IOException) {
            logger.e("Cannot parse Activities from xml", e)
        }
        return activities
    }

    @SuppressWarnings("unused")
    suspend fun getPackageName(apkFile: File): String {
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
            logger.e("Cannot parse package name from xml", e)
        } catch (e: IOException) {
            logger.e("Cannot parse package name from xml", e)
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
    private suspend fun getParserForManifest(
        apkFile: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): XmlResourceParser {
        return withContext(dispatcher) {
            val assetManagerInstance = assetManager
            val cookie = addAssets(apkFile, assetManagerInstance!!)
            return@withContext (assetManagerInstance as AssetManager).openXmlResourceParser(
                cookie, "AndroidManifest.xml"
            )
        }
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
                "addAssetPath", String::class.java
            )
            return addAssetPath.invoke(
                assetManagerInstance,
                apkFile.absolutePath
            ) as Int
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
