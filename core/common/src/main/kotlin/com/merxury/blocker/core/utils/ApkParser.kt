/*
 * Copyright 2025 Blocker
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
import com.merxury.blocker.core.model.data.ActivityIntentFilterInfo
import com.merxury.blocker.core.model.data.IntentFilterDataInfo
import com.merxury.blocker.core.model.data.IntentFilterInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.File
import java.io.IOException

object ApkParser {

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
                return assetManagerClass.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                Timber.e(e, "Cannot create AssetManager")
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
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
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
                Timber.e(e, "Error occurs in parsing manifest")
            } catch (e: IOException) {
                Timber.e(e, "Cannot parse manifest")
            }
            return@withContext -1
        }
    }

    suspend fun getActivities(pm: PackageManager, packageName: String): MutableList<ActivityInfo> {
        val activities = mutableListOf<ActivityInfo>()
        try {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0) ?: return activities
            val sourceDir = packageInfo.applicationInfo?.sourceDir ?: throw IOException("Cannot get sourceDir")
            val parser = getParserForManifest(File(sourceDir))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "activity") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "name") {
                            val componentInfo = ActivityInfo()
                            componentInfo.packageName = packageName
                            componentInfo.name = parser.getAttributeValue(i)
                            componentInfo.enabled = ApplicationUtil.checkComponentIsEnabled(
                                pm,
                                ComponentName(componentInfo.packageName, componentInfo.name),
                            )
                            activities.add(componentInfo)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            Timber.e(e, "Cannot parse Activities from xml")
        } catch (e: IOException) {
            Timber.e(e, "Cannot parse Activities from xml")
        }
        return activities
    }

    suspend fun getServices(pm: PackageManager, packageName: String): MutableList<ServiceInfo> {
        val services = mutableListOf<ServiceInfo>()
        try {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0) ?: return services
            val sourceDir = packageInfo.applicationInfo?.sourceDir ?: throw IOException("Cannot get sourceDir")
            val parser = getParserForManifest(File(sourceDir))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "service") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "name") {
                            val componentInfo = ServiceInfo()
                            componentInfo.packageName = packageName
                            componentInfo.name = parser.getAttributeValue(i)
                            componentInfo.enabled = ApplicationUtil.checkComponentIsEnabled(
                                pm,
                                ComponentName(componentInfo.packageName, componentInfo.name),
                            )
                            services.add(componentInfo)
                        }
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            Timber.e(e, "Cannot parse services from xml")
        } catch (e: IOException) {
            Timber.e(e, "Cannot parse services from xml")
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
            Timber.e(e, "Cannot parse package name from xml")
        } catch (e: IOException) {
            Timber.e(e, "Cannot parse package name from xml")
        }
        return ""
    }

    /**
     * Extracts activities with specific intent filters from an APK file
     *
     * Filters activities that have intent-filter containing any of:
     * - action: android.intent.action.VIEW
     * - action: android.intent.action.SEND
     * - action: android.intent.action.SEND_MULTIPLE
     * - category: android.intent.category.BROWSABLE
     *
     * @param apkFile the APK file to parse
     * @param dispatcher the coroutine dispatcher for I/O operations
     * @return list of activities matching the filter criteria with complete intent filter information
     */
    suspend fun getActivitiesWithIntentFilters(
        apkFile: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): MutableList<ActivityIntentFilterInfo> {
        val results = mutableListOf<ActivityIntentFilterInfo>()
        return withContext(dispatcher) {
            try {
                val packageName = getPackageName(apkFile)
                if (packageName.isEmpty()) return@withContext results

                val parser = getParserForManifest(apkFile, dispatcher)
                var currentActivityName: String? = null
                var currentActivityExported: Boolean? = null
                var currentActivityLabel: String? = null
                val currentActivityIntentFilters = mutableListOf<IntentFilterInfo>()

                val currentFilterActions = mutableListOf<String>()
                val currentFilterCategories = mutableListOf<String>()
                val currentFilterData = mutableListOf<IntentFilterDataInfo>()

                var insideActivity = false
                var insideIntentFilter = false
                var activityDepth = 0
                var currentDepth = 0

                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            currentDepth++
                            when (parser.name) {
                                "activity" -> {
                                    insideActivity = true
                                    activityDepth = currentDepth
                                    currentActivityName = null
                                    currentActivityExported = null
                                    currentActivityLabel = null
                                    currentActivityIntentFilters.clear()

                                    for (i in 0 until parser.attributeCount) {
                                        when (parser.getAttributeName(i)) {
                                            "name" -> currentActivityName = parser.getAttributeValue(i)
                                            "exported" -> currentActivityExported = parser.getAttributeBooleanValue(i, false)
                                            "label" -> currentActivityLabel = parser.getAttributeValue(i)
                                        }
                                    }
                                }
                                "intent-filter" -> {
                                    if (insideActivity) {
                                        insideIntentFilter = true
                                        currentFilterActions.clear()
                                        currentFilterCategories.clear()
                                        currentFilterData.clear()
                                    }
                                }
                                "action" -> {
                                    if (insideIntentFilter) {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i) == "name") {
                                                currentFilterActions.add(parser.getAttributeValue(i))
                                            }
                                        }
                                    }
                                }
                                "category" -> {
                                    if (insideIntentFilter) {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i) == "name") {
                                                currentFilterCategories.add(parser.getAttributeValue(i))
                                            }
                                        }
                                    }
                                }
                                "data" -> {
                                    if (insideIntentFilter) {
                                        var scheme: String? = null
                                        var host: String? = null
                                        var port: String? = null
                                        var path: String? = null
                                        var pathPrefix: String? = null
                                        var pathPattern: String? = null
                                        var mimeType: String? = null

                                        for (i in 0 until parser.attributeCount) {
                                            when (parser.getAttributeName(i)) {
                                                "scheme" -> scheme = parser.getAttributeValue(i)
                                                "host" -> host = parser.getAttributeValue(i)
                                                "port" -> port = parser.getAttributeValue(i)
                                                "path" -> path = parser.getAttributeValue(i)
                                                "pathPrefix" -> pathPrefix = parser.getAttributeValue(i)
                                                "pathPattern" -> pathPattern = parser.getAttributeValue(i)
                                                "mimeType" -> mimeType = parser.getAttributeValue(i)
                                            }
                                        }

                                        currentFilterData.add(
                                            IntentFilterDataInfo(
                                                scheme = scheme,
                                                host = host,
                                                port = port,
                                                path = path,
                                                pathPrefix = pathPrefix,
                                                pathPattern = pathPattern,
                                                mimeType = mimeType,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "intent-filter" -> {
                                    if (insideIntentFilter) {
                                        currentActivityIntentFilters.add(
                                            IntentFilterInfo(
                                                actions = currentFilterActions.toList(),
                                                categories = currentFilterCategories.toList(),
                                                data = currentFilterData.toList(),
                                            ),
                                        )
                                        insideIntentFilter = false
                                    }
                                }
                                "activity" -> {
                                    if (insideActivity && currentDepth == activityDepth) {
                                        val shouldInclude = currentActivityIntentFilters.any { filter ->
                                            filter.actions.any {
                                                it == "android.intent.action.VIEW" ||
                                                    it == "android.intent.action.SEND" ||
                                                    it == "android.intent.action.SEND_MULTIPLE"
                                            } ||
                                                filter.categories.any { it == "android.intent.category.BROWSABLE" }
                                        }

                                        if (shouldInclude && currentActivityName != null) {
                                            val exported = currentActivityExported
                                                ?: currentActivityIntentFilters.isNotEmpty()

                                            results.add(
                                                ActivityIntentFilterInfo(
                                                    name = currentActivityName,
                                                    packageName = packageName,
                                                    exported = exported,
                                                    label = currentActivityLabel,
                                                    intentFilters = currentActivityIntentFilters.toList(),
                                                ),
                                            )
                                        }

                                        insideActivity = false
                                    }
                                }
                            }
                            currentDepth--
                        }
                    }
                }
            } catch (e: XmlPullParserException) {
                Timber.e(e, "Cannot parse activities with intent filters from xml")
            } catch (e: IOException) {
                Timber.e(e, "Cannot parse activities with intent filters from xml")
            }
            return@withContext results
        }
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
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): XmlResourceParser {
        return withContext(dispatcher) {
            val assetManagerInstance = assetManager
            val cookie = addAssets(apkFile, assetManagerInstance!!)
            return@withContext (assetManagerInstance as AssetManager).openXmlResourceParser(
                cookie,
                "AndroidManifest.xml",
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
                "addAssetPath",
                String::class.java,
            )
            return addAssetPath.invoke(
                assetManagerInstance,
                apkFile.absolutePath,
            ) as Int
        } catch (e: Exception) {
            Timber.e(e, "Cannot access addAssetPath")
        }
        return -1
    }
}
