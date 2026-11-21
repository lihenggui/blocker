// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.os.Build
import com.merxury.blocker.core.controllers.wake.WakePackageUid
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest
import javax.inject.Inject

internal fun ByteArray.hexDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    val alphabet = "0123456789abcdef"
    val text = CharArray(digest.size * 2)
    digest.forEachIndexed { i, byte ->
        val value = byte.toInt() and 255
        text[i * 2] = alphabet[value ushr 4]
        text[i * 2 + 1] = alphabet[value and 15]
    }
    return text.concatToString()
}

internal data class WakePackageLabel(val uid: Int, val packageName: String, val since: Long)

class WakeIdentityIndex @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: WakeHistoryDao,
) {
    @Suppress("DEPRECATION")
    internal fun installedPackages(): List<PackageInfo> = context.packageManager.getInstalledPackages(0)

    internal fun labels(local: List<PackageInfo>, remote: List<WakePackageUid>, now: Long): Map<Int, List<WakePackageLabel>> {
        val localLabels = local.mapNotNull {
            val uid = it.applicationInfo?.uid ?: return@mapNotNull null
            WakePackageLabel(uid, it.packageName, maxOf(it.firstInstallTime, it.lastUpdateTime).takeIf { time -> time in 1..now } ?: now)
        }
        val otherLabels = remote.map { WakePackageLabel(it.uid, it.packageName, now) }
        return (localLabels + otherLabels).distinctBy { it.uid to it.packageName }.groupBy { it.uid }
    }

    @Suppress("DEPRECATION")
    internal suspend fun refresh(local: List<PackageInfo>, remote: List<WakePackageUid>, now: Long): List<WakeIdentity> {
        val pm = context.packageManager
        val known = dao.identities(now - 14L * 86400000).associateBy { Triple(it.uid, it.packageName, it.revision) }
        val signaturesFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val flags = signaturesFlag or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS or PackageManager.MATCH_DISABLED_COMPONENTS
        val result = mutableListOf<WakeIdentity>()
        val pending = ArrayList<WakeIdentity>(20)
        suspend fun record(identity: WakeIdentity) {
            // Keep installed identities cheap to correlate, and checkpoint a large first scan.
            result.add(identity.copy(components = ""))
            pending.add(identity)
            if (pending.size == 20) {
                dao.index(pending)
                pending.clear()
            }
        }
        for (summary in local) {
            val uid = summary.applicationInfo?.uid ?: continue
            val version = if (Build.VERSION.SDK_INT >= 28) summary.longVersionCode else summary.versionCode.toLong()
            val revision = "$version/${summary.lastUpdateTime}/${summary.firstInstallTime}"
            val previous = known[Triple(uid, summary.packageName, revision)]
            if (previous != null) {
                record(previous.copy(lastSeen = now))
                continue
            }
            val info = try {
                pm.getPackageInfo(summary.packageName, flags)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            record(
                WakeIdentity(
                    uid,
                    info.packageName,
                    revision,
                    version,
                    signatures.orEmpty().map { it.toByteArray().hexDigest() }.sorted().joinToString(","),
                    components(info).toString(),
                    maxOf(info.firstInstallTime, info.lastUpdateTime).takeIf { it in 1..now } ?: now,
                    now,
                ),
            )
        }
        val localKeys = result.map { it.uid to it.packageName }.toSet()
        for (other in remote) {
            if (other.uid to other.packageName in localKeys) continue
            val revision = "profile/${other.version}/${other.apkPath}"
            val prior = known[Triple(other.uid, other.packageName, revision)]
            if (prior != null && prior.signatures.isNotEmpty()) {
                record(prior.copy(lastSeen = now))
                continue
            }
            val archive = other.apkPath?.let { path ->
                runCatching { pm.getPackageArchiveInfo(path, flags) }.getOrNull()?.takeIf { it.packageName == other.packageName }?.apply {
                    applicationInfo?.sourceDir = path
                    applicationInfo?.publicSourceDir = path
                }
            }
            val signers = if (Build.VERSION.SDK_INT >= 28) archive?.signingInfo?.apkContentsSigners else archive?.signatures
            record(
                WakeIdentity(
                    other.uid,
                    other.packageName,
                    revision,
                    other.version ?: -1L,
                    signers.orEmpty().map { it.toByteArray().hexDigest() }.sorted().joinToString(","),
                    archive?.let { components(it).toString() } ?: "[]",
                    prior?.firstSeen ?: now,
                    now,
                ),
            )
        }
        if (pending.isNotEmpty()) dao.index(pending)
        return result
    }

    private fun components(info: PackageInfo): JSONArray {
        val actions = mutableMapOf<String, MutableSet<String>>()
        try {
            val assets = context.packageManager.getResourcesForApplication(requireNotNull(info.applicationInfo)).assets
            assets.openXmlResourceParser("AndroidManifest.xml").use { xml ->
                var owner: String? = null
                var depth = -1
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.END_TAG && xml.depth == depth) owner = null
                    if (xml.eventType != XmlPullParser.START_TAG) continue
                    val name = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name") ?: continue
                    when (xml.name) {
                        "activity", "activity-alias", "receiver", "service", "provider" -> {
                            owner = when {
                                name.startsWith('.') -> info.packageName + name
                                '.' !in name -> "${info.packageName}.$name"
                                else -> name
                            }
                            depth = xml.depth
                        }

                        "action" -> owner?.let { actions.getOrPut(it) { mutableSetOf() }.add(name) }
                    }
                }
            }
        } catch (_: Exception) {
            // Package visibility and split resources can limit manifest metadata.
        }
        val array = JSONArray()
        fun add(type: String, items: Array<out ComponentInfo>?) {
            for (component in items.orEmpty()) {
                val obj = JSONObject().put("type", type).put("name", component.name).put("process", component.processName)
                    .put("exported", component.exported).put("enabled", component.enabled)
                    .put("actions", JSONArray(actions[component.name].orEmpty().toList()))
                when (component) {
                    is ProviderInfo -> obj.put("authority", component.authority).put("readPermission", component.readPermission).put("writePermission", component.writePermission)
                    is ServiceInfo -> obj.put("permission", component.permission)
                    is android.content.pm.ActivityInfo -> obj.put("permission", component.permission)
                }
                array.put(obj)
            }
        }
        add("activity", info.activities)
        add("receiver", info.receivers)
        add("service", info.services)
        add("provider", info.providers)
        return array
    }
}
