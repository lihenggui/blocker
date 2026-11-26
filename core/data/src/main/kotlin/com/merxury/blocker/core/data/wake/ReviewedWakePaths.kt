// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.merxury.blocker.core.database.wake.WakeIdentity
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/** Only reviews distributed with Blocker may authorize an automatic system change. */
class ReviewedWakePaths @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val catalog by lazy {
        context.assets.list("reviewed-wake-paths").orEmpty().filter { it.endsWith(".json") }.flatMap { file ->
            val entries = context.assets.open("reviewed-wake-paths/$file").bufferedReader().use { JSONArray(it.readText()) }
            List(entries.length()) { entries.getJSONObject(it) }
        }
    }

    fun evaluate(requests: List<WakeRecord>, identities: List<WakeIdentity>, profiles: List<Int>, previous: List<WakeProtection>, firewallResets: Map<String, Long> = emptyMap()): List<WakeProtection> {
        if (0 !in profiles) return emptyList()
        val (owner, secondary) = identities.partition { userOfUid(it.uid) == 0 }
        val byUid = owner.groupBy { it.uid }
        val byPackage = owner.groupBy { it.packageName }
        val secondaryByPackage = secondary.groupBy { it.packageName }
        val paths = mutableMapOf<String, WakeProtection>()
        val digests = mutableMapOf<String, Set<String>>()
        for (proof in catalog) {
            val component = proof.getString("component")
            val action = if (proof.isNull("action")) "" else proof.getString("action")
            val method = proof.optString("method", "IFW")
            val review = proof.getString("review")
            val fixedPackage = proof.optString("target")
            val fixedCaller = proof.optString("caller")
            val peerSdk = proof.optBoolean("peerSdk")

            // Intent Firewall matches packages across users.
            fun conflicts(identity: WakeIdentity): Boolean = method == "IFW" && secondaryByPackage[identity.packageName].orEmpty().isNotEmpty()
            fun matchesCaller(caller: WakeIdentity, target: WakeIdentity): Boolean = (fixedCaller.isEmpty() || caller.packageName == fixedCaller) &&
                (!(peerSdk || fixedCaller.isNotEmpty()) || !conflicts(caller)) &&
                (!peerSdk || (caller.uid != target.uid && caller.uid >= 10000 && matchesSdk(caller, proof)))

            val targets = if (fixedPackage.isNotEmpty()) {
                setOf(fixedPackage)
            } else {
                val observed = requests.mapNotNull { event ->
                    val target = byUid[event.targetUid]?.singleOrNull() ?: return@mapNotNull null
                    target.packageName.takeIf { matchesEvent(event, it, proof, method, component, action, firewallResets) }
                }
                (observed + previous.filter { it.component == component && it.proof == review }.map { it.target }).toSet()
            }
            for (target in targets.flatMap { byPackage[it].orEmpty() }) {
                if (byUid[target.uid]?.size != 1 || target.uid < 10000 || target.signatures.isBlank() || review.isBlank() || conflicts(target)) continue
                if (fixedPackage.isNotEmpty()) {
                    if (target.versionCode != proof.getLong("version") || target.signatures != proof.getString("signers") ||
                        apkDigests(target.packageName, digests) != proof.getJSONArray("apks").strings()
                    ) {
                        continue
                    }
                } else if (!matchesSdk(target, proof)) {
                    continue
                }
                for (old in previous.filter { it.target == target.packageName && it.component == component && it.action == action && it.method == method }) {
                    val caller = byPackage[old.caller]?.singleOrNull()
                    if (old.targetRevision == target.revision && old.proof == review && caller != null &&
                        caller.revision == old.callerRevision && byUid[caller.uid]?.size == 1 && !old.disabledByUser &&
                        matchesCaller(caller, target)
                    ) {
                        paths[old.pathId] = old
                    }
                }
                for (event in requests) {
                    if (event.targetUid != target.uid || target.firstSeen > event.wallMillis ||
                        (event.targetPackage != null && event.targetPackage != target.packageName)
                    ) {
                        continue
                    }
                    val caller = if (method == "PM") target else byUid[event.callerUid]?.singleOrNull() ?: continue
                    if (caller.firstSeen > event.wallMillis || (method == "IFW" && event.callerPackage != null && event.callerPackage != caller.packageName)) continue
                    if (!matchesCaller(caller, target)) continue
                    if (!matchesEvent(event, target.packageName, proof, method, component, action, firewallResets)) continue
                    val id = listOf(caller.packageName, caller.signatures, target.packageName, target.signatures, component, action, method).joinToString("|")
                    val old = previous.find { it.pathId == id && it.targetRevision == target.revision && it.callerRevision == caller.revision }
                    if (old?.disabledByUser == true) continue
                    paths[id] = WakeProtection(
                        id, caller.packageName, target.packageName, component, action, review, false, old?.configured == true,
                        method, caller.revision, target.revision, target.signatures, old?.previousState, old?.pendingChange == true,
                    )
                }
            }
        }
        return paths.values.toList()
    }

    private fun matchesEvent(
        event: WakeRecord,
        target: String,
        proof: JSONObject,
        method: String,
        component: String,
        action: String,
        firewallResets: Map<String, Long>,
    ): Boolean {
        if (event.action.orEmpty() != action || qualifiedWakeComponent(target, event.component) != component) return false
        return when (method) {
            "IFW" -> {
                val resetAt = listOfNotNull(firewallResets[""], firewallResets[target]).maxOrNull()
                when (event.source) {
                    "statsd" -> event.kind == "ServiceRequestEventReported" && event.wallMillis > (resetAt ?: 0L)

                    // A held binding records the inspection time. Its start may predate the reset.
                    "SERVICES" -> event.kind == "HeldConnection" && action.isNotEmpty() && resetAt == null

                    else -> false
                }
            }

            // Only a review covering every invocation can authorize a component-wide change.
            "PM" -> proof.optBoolean("dedicated") && event.kind in setOf("ProcessStartTime", "am_proc_start", "ServiceRequestEventReported")

            else -> false
        }
    }

    private val sdkCache = context.getSharedPreferences("wake_reviewed_sdk", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun matchesSdk(identity: WakeIdentity, proof: JSONObject): Boolean {
        val prefix = proof.getString("sdkNamespace")
        val expected = proof.getString("sdkFingerprint")
        val key = "${identity.packageName}/$prefix"
        val revision = "${identity.revision}/${identity.signatures}/$expected"
        val cached = sdkCache.getString(key, null)
        if (cached == "$revision/y") return true
        if (cached == "$revision/n") return false
        val app = try {
            context.packageManager.getApplicationInfo(identity.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val files = (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).map(::File)
        val fingerprint = try {
            SdkDexFingerprint.compute(files, prefix)
        } catch (e: java.io.IOException) {
            Timber.w(e, "Cannot read SDK fingerprint for %s", identity.packageName)
            return false
        } catch (e: RuntimeException) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Invalid SDK fingerprint input for %s", identity.packageName)
            return false
        }
        val matches = fingerprint == expected
        sdkCache.edit { putString(key, "$revision/" + if (matches) "y" else "n") }
        return matches
    }

    fun currentState(path: WakeProtection): Int? = try {
        context.packageManager.getComponentEnabledSetting(ComponentName(path.target, path.component))
    } catch (_: IllegalArgumentException) {
        null
    }

    @Suppress("DEPRECATION")
    fun isCurrent(path: WakeProtection): Boolean {
        fun revision(pkg: String): String? {
            val info = try {
                context.packageManager.getPackageInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return null
            }
            return info.wakeRevision
        }
        return revision(path.target) == path.targetRevision && (path.caller == path.target || revision(path.caller) == path.callerRevision)
    }

    @Suppress("DEPRECATION")
    fun sameInstallation(path: WakeProtection): Boolean {
        val pm = context.packageManager
        val flag = wakeSigningFlags
        val info = try {
            pm.getPackageInfo(path.target, flag)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        if (path.targetRevision.substringAfterLast('/').toLongOrNull() != info.firstInstallTime) return false
        return info.wakeSigners == path.targetSigners
    }

    @Suppress("DEPRECATION")
    private fun apkDigests(pkg: String, cache: MutableMap<String, Set<String>>): Set<String> = cache.getOrPut(pkg) {
        val app = context.packageManager.getApplicationInfo(pkg, 0)
        (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).map { path ->
            val digest = MessageDigest.getInstance("SHA-256")
            File(path).inputStream().buffered().use { input ->
                val buf = ByteArray(65536)
                while (true) {
                    val count = input.read(buf)
                    if (count < 0) break
                    digest.update(buf, 0, count)
                }
            }
            digest.digest().toHexString()
        }.toSet()
    }

    private fun JSONArray.strings(): Set<String> = (0 until length()).map { getString(it) }.toSet()
}
