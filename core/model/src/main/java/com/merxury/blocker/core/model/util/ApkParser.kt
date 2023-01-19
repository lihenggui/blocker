/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.model.util

import android.content.res.AssetManager
import android.content.res.XmlResourceParser
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
                return assetManagerClass.newInstance()
            } catch (e: Exception) {
                Timber.e("Cannot create AssetManager", e)
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
                Timber.e("Error occurs in parsing manifest", e)
            } catch (e: IOException) {
                Timber.e("Cannot parse manifest", e)
            }
            return@withContext -1
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
            Timber.e("Cannot access addAssetPath", e)
        }
        return -1
    }
}
