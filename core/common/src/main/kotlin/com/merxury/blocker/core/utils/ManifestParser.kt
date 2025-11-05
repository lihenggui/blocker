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

import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.IntentFilterData
import com.merxury.blocker.core.model.manifest.ManifestActivity
import com.merxury.blocker.core.model.manifest.ManifestApplication
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestMetaData
import com.merxury.blocker.core.model.manifest.ManifestPermission
import com.merxury.blocker.core.model.manifest.ManifestProvider
import com.merxury.blocker.core.model.manifest.ManifestReceiver
import com.merxury.blocker.core.model.manifest.ManifestService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Parser for AndroidManifest.xml files that creates a complete object-oriented representation.
 *
 * This parser performs a single-pass traversal of the manifest XML to build a complete
 * [AndroidManifest] object containing all components with their intent filters, permissions,
 * and metadata.
 */
object ManifestParser {

    private const val NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android"

    /**
     * Parses an APK file and returns its complete manifest representation.
     *
     * @param apkFile The APK file to parse
     * @param dispatcher The coroutine dispatcher for I/O operations
     * @return Result containing the parsed AndroidManifest or an error
     */
    suspend fun parseManifest(
        apkFile: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<AndroidManifest> = withContext(dispatcher) {
        try {
            val parser = getParserForManifest(apkFile, dispatcher)
            val manifest = parseManifestXml(parser)
            Result.success(manifest)
        } catch (e: XmlPullParserException) {
            Timber.e(e, "XML parsing error in manifest")
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "I/O error reading manifest")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error parsing manifest")
            Result.failure(e)
        }
    }

    private fun parseManifestXml(parser: XmlResourceParser): AndroidManifest {
        var packageName = ""
        var versionCode: Int? = null
        var versionName: String? = null
        var minSdkVersion: Int? = null
        var targetSdkVersion: Int? = null
        val usesPermissions = mutableListOf<ManifestPermission>()
        val permissions = mutableListOf<ManifestPermission>()
        var application: ManifestApplication? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "manifest" -> {
                    packageName = parser.getAttributeValue(null, "package") ?: ""
                    versionCode = parser.getAttributeIntValue(NAMESPACE_ANDROID, "versionCode", -1)
                        .takeIf { it != -1 }
                    versionName = parser.getAttributeValue(NAMESPACE_ANDROID, "versionName")
                }
                "uses-sdk" -> {
                    minSdkVersion = parser.getAttributeIntValue(NAMESPACE_ANDROID, "minSdkVersion", -1)
                        .takeIf { it != -1 }
                    targetSdkVersion = parser.getAttributeIntValue(NAMESPACE_ANDROID, "targetSdkVersion", -1)
                        .takeIf { it != -1 }
                }
                "uses-permission" -> {
                    val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name")
                    if (name != null) {
                        usesPermissions.add(ManifestPermission(name = name))
                    }
                }
                "permission" -> {
                    val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name")
                    val protectionLevel = parser.getAttributeValue(NAMESPACE_ANDROID, "protectionLevel")
                    val description = parser.getAttributeValue(NAMESPACE_ANDROID, "description")
                    if (name != null) {
                        permissions.add(
                            ManifestPermission(
                                name = name,
                                protectionLevel = protectionLevel,
                                description = description,
                            ),
                        )
                    }
                }
                "application" -> {
                    application = parseApplication(parser)
                }
            }
        }

        return AndroidManifest(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdkVersion = minSdkVersion,
            targetSdkVersion = targetSdkVersion,
            usesPermissions = usesPermissions,
            permissions = permissions,
            application = application ?: ManifestApplication(),
        )
    }

    private fun parseApplication(parser: XmlResourceParser): ManifestApplication {
        val appLabel = parser.getAttributeValue(NAMESPACE_ANDROID, "label")
        val appIcon = parser.getAttributeValue(NAMESPACE_ANDROID, "icon")
        val appTheme = parser.getAttributeValue(NAMESPACE_ANDROID, "theme")
        val debuggable = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "debuggable", false)
        val allowBackup = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "allowBackup", true)

        val activities = mutableListOf<ManifestActivity>()
        val services = mutableListOf<ManifestService>()
        val receivers = mutableListOf<ManifestReceiver>()
        val providers = mutableListOf<ManifestProvider>()
        val metaData = mutableListOf<ManifestMetaData>()

        val applicationDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == applicationDepth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "activity" -> activities.add(parseActivity(parser))
                "service" -> services.add(parseService(parser))
                "receiver" -> receivers.add(parseReceiver(parser))
                "provider" -> providers.add(parseProvider(parser))
                "meta-data" -> metaData.add(parseMetaData(parser))
            }
        }

        return ManifestApplication(
            label = appLabel,
            icon = appIcon,
            theme = appTheme,
            debuggable = debuggable,
            allowBackup = allowBackup,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            metaData = metaData,
        )
    }

    private fun parseActivity(parser: XmlResourceParser): ManifestActivity {
        val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name") ?: ""
        val label = parser.getAttributeValue(NAMESPACE_ANDROID, "label")
        val icon = parser.getAttributeValue(NAMESPACE_ANDROID, "icon")
        val enabled = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "enabled", true)
        val exported = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "exported", false)
        val permission = parser.getAttributeValue(NAMESPACE_ANDROID, "permission")
        val process = parser.getAttributeValue(NAMESPACE_ANDROID, "process")
        val launchMode = parser.getAttributeValue(NAMESPACE_ANDROID, "launchMode")
        val screenOrientation = parser.getAttributeValue(NAMESPACE_ANDROID, "screenOrientation")
        val theme = parser.getAttributeValue(NAMESPACE_ANDROID, "theme")
        val taskAffinity = parser.getAttributeValue(NAMESPACE_ANDROID, "taskAffinity")

        val intentFilters = mutableListOf<ManifestIntentFilter>()
        val metaData = mutableListOf<ManifestMetaData>()
        val componentDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == componentDepth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "intent-filter" -> intentFilters.add(parseIntentFilter(parser))
                "meta-data" -> metaData.add(parseMetaData(parser))
            }
        }

        return ManifestActivity(
            name = name,
            label = label,
            icon = icon,
            enabled = enabled,
            exported = exported,
            permission = permission,
            process = process,
            metaData = metaData,
            intentFilters = intentFilters,
            launchMode = launchMode,
            screenOrientation = screenOrientation,
            theme = theme,
            taskAffinity = taskAffinity,
        )
    }

    private fun parseService(parser: XmlResourceParser): ManifestService {
        val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name") ?: ""
        val label = parser.getAttributeValue(NAMESPACE_ANDROID, "label")
        val icon = parser.getAttributeValue(NAMESPACE_ANDROID, "icon")
        val enabled = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "enabled", true)
        val exported = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "exported", false)
        val permission = parser.getAttributeValue(NAMESPACE_ANDROID, "permission")
        val process = parser.getAttributeValue(NAMESPACE_ANDROID, "process")
        val foregroundServiceType = parser.getAttributeValue(NAMESPACE_ANDROID, "foregroundServiceType")

        val intentFilters = mutableListOf<ManifestIntentFilter>()
        val metaData = mutableListOf<ManifestMetaData>()
        val componentDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == componentDepth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "intent-filter" -> intentFilters.add(parseIntentFilter(parser))
                "meta-data" -> metaData.add(parseMetaData(parser))
            }
        }

        return ManifestService(
            name = name,
            label = label,
            icon = icon,
            enabled = enabled,
            exported = exported,
            permission = permission,
            process = process,
            metaData = metaData,
            intentFilters = intentFilters,
            foregroundServiceType = foregroundServiceType,
        )
    }

    private fun parseReceiver(parser: XmlResourceParser): ManifestReceiver {
        val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name") ?: ""
        val label = parser.getAttributeValue(NAMESPACE_ANDROID, "label")
        val icon = parser.getAttributeValue(NAMESPACE_ANDROID, "icon")
        val enabled = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "enabled", true)
        val exported = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "exported", false)
        val permission = parser.getAttributeValue(NAMESPACE_ANDROID, "permission")
        val process = parser.getAttributeValue(NAMESPACE_ANDROID, "process")

        val intentFilters = mutableListOf<ManifestIntentFilter>()
        val metaData = mutableListOf<ManifestMetaData>()
        val componentDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == componentDepth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "intent-filter" -> intentFilters.add(parseIntentFilter(parser))
                "meta-data" -> metaData.add(parseMetaData(parser))
            }
        }

        return ManifestReceiver(
            name = name,
            label = label,
            icon = icon,
            enabled = enabled,
            exported = exported,
            permission = permission,
            process = process,
            metaData = metaData,
            intentFilters = intentFilters,
        )
    }

    private fun parseProvider(parser: XmlResourceParser): ManifestProvider {
        val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name") ?: ""
        val label = parser.getAttributeValue(NAMESPACE_ANDROID, "label")
        val icon = parser.getAttributeValue(NAMESPACE_ANDROID, "icon")
        val enabled = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "enabled", true)
        val exported = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "exported", false)
        val permission = parser.getAttributeValue(NAMESPACE_ANDROID, "permission")
        val process = parser.getAttributeValue(NAMESPACE_ANDROID, "process")
        val authorities = parser.getAttributeValue(NAMESPACE_ANDROID, "authorities")
        val grantUriPermissions = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "grantUriPermissions", false)
        val readPermission = parser.getAttributeValue(NAMESPACE_ANDROID, "readPermission")
        val writePermission = parser.getAttributeValue(NAMESPACE_ANDROID, "writePermission")

        val metaData = mutableListOf<ManifestMetaData>()
        val componentDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == componentDepth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            if (parser.name == "meta-data") {
                metaData.add(parseMetaData(parser))
            }
        }

        return ManifestProvider(
            name = name,
            label = label,
            icon = icon,
            enabled = enabled,
            exported = exported,
            permission = permission,
            process = process,
            metaData = metaData,
            authorities = authorities,
            grantUriPermissions = grantUriPermissions,
            readPermission = readPermission,
            writePermission = writePermission,
        )
    }

    private fun parseIntentFilter(parser: XmlResourceParser): ManifestIntentFilter {
        val priority = parser.getAttributeIntValue(NAMESPACE_ANDROID, "priority", Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        val autoVerify = parser.getAttributeBooleanValue(NAMESPACE_ANDROID, "autoVerify", false)

        val actions = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val dataList = mutableListOf<IntentFilterData>()
        val filterDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == filterDepth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "action" -> {
                    val actionName = parser.getAttributeValue(NAMESPACE_ANDROID, "name")
                    if (actionName != null) {
                        actions.add(actionName)
                    }
                }
                "category" -> {
                    val categoryName = parser.getAttributeValue(NAMESPACE_ANDROID, "name")
                    if (categoryName != null) {
                        categories.add(categoryName)
                    }
                }
                "data" -> {
                    dataList.add(parseIntentFilterData(parser))
                }
            }
        }

        return ManifestIntentFilter(
            actions = actions,
            categories = categories,
            data = dataList,
            priority = priority,
            autoVerify = autoVerify,
        )
    }

    private fun parseIntentFilterData(parser: XmlResourceParser): IntentFilterData = IntentFilterData(
        scheme = parser.getAttributeValue(NAMESPACE_ANDROID, "scheme"),
        host = parser.getAttributeValue(NAMESPACE_ANDROID, "host"),
        port = parser.getAttributeValue(NAMESPACE_ANDROID, "port"),
        path = parser.getAttributeValue(NAMESPACE_ANDROID, "path"),
        pathPrefix = parser.getAttributeValue(NAMESPACE_ANDROID, "pathPrefix"),
        pathPattern = parser.getAttributeValue(NAMESPACE_ANDROID, "pathPattern"),
        mimeType = parser.getAttributeValue(NAMESPACE_ANDROID, "mimeType"),
    )

    private fun parseMetaData(parser: XmlResourceParser): ManifestMetaData {
        val name = parser.getAttributeValue(NAMESPACE_ANDROID, "name") ?: ""
        val value = parser.getAttributeValue(NAMESPACE_ANDROID, "value")
        val resource = parser.getAttributeValue(NAMESPACE_ANDROID, "resource")

        return ManifestMetaData(
            name = name,
            value = value,
            resource = resource,
        )
    }

    private val assetManager: Any?
        get() {
            return try {
                val assetManagerClass = Class.forName("android.content.res.AssetManager")
                assetManagerClass.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                Timber.e(e, "Cannot create AssetManager")
                null
            }
        }

    @Throws(IOException::class)
    private suspend fun getParserForManifest(
        apkFile: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): XmlResourceParser = withContext(dispatcher) {
        val assetManagerInstance = assetManager
            ?: throw IOException("Failed to create AssetManager")
        val cookie = addAssets(apkFile, assetManagerInstance)
        if (cookie == -1) {
            throw IOException("Failed to add assets for ${apkFile.absolutePath}")
        }
        return@withContext (assetManagerInstance as AssetManager).openXmlResourceParser(
            cookie,
            "AndroidManifest.xml",
        )
    }

    private fun addAssets(apkFile: File, assetManagerInstance: Any): Int = try {
        val addAssetPath = assetManagerInstance.javaClass.getMethod(
            "addAssetPath",
            String::class.java,
        )
        addAssetPath.invoke(assetManagerInstance, apkFile.absolutePath) as Int
    } catch (e: Exception) {
        Timber.e(e, "Cannot access addAssetPath")
        -1
    }
}
