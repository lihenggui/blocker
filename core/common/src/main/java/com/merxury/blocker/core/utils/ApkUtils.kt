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

package com.merxury.blocker.core.utils

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import com.merxury.blocker.core.extension.getPackageInfoCompat
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber

object ApkUtils {

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
                Timber.e("Cannot create AssetManager", e)
            } catch (e: InstantiationException) {
                Timber.e("Cannot create AssetManager", e)
            } catch (e: IllegalAccessException) {
                Timber.e("Cannot create AssetManager", e)
            }

            return null
        }

    suspend fun getActivities(pm: PackageManager, packageName: String): MutableList<ActivityInfo> {
        val activities = mutableListOf<ActivityInfo>()
        try {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0)
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
            Timber.e("Cannot parse Activities from xml", e)
        } catch (e: IOException) {
            Timber.e("Cannot parse Activities from xml", e)
        }
        return activities
    }

    suspend fun getServices(pm: PackageManager, packageName: String): MutableList<ServiceInfo> {
        val services = mutableListOf<ServiceInfo>()
        try {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0)
            val parser = getParserForManifest(File(packageInfo.applicationInfo.sourceDir))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "service") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "name") {
                            val componentInfo = ServiceInfo()
                            componentInfo.packageName = packageName
                            componentInfo.name = parser.getAttributeValue(i)
                            componentInfo.enabled = ApplicationUtil.checkComponentIsEnabled(
                                pm,
                                ComponentName(componentInfo.packageName, componentInfo.name)
                            )
                            services.add(componentInfo)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            Timber.e("Cannot parse services from xml", e)
        } catch (e: IOException) {
            Timber.e("Cannot parse services from xml", e)
        }
        return services
    }

    suspend fun getPackageName(apkFile: File): String {
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
            Timber.e("Cannot parse package name from xml", e)
        } catch (e: IOException) {
            Timber.e("Cannot parse package name from xml", e)
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
