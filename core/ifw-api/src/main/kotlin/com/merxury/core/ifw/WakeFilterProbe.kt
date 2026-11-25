// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.UUID

class WakeFilterProbeService : Service() {
    override fun onBind(intent: Intent): IBinder = Binder()
}

/** Some framework builds throw a cross-user SecurityException back into an ordinary caller. */
internal suspend fun testPeerFilter(context: Context, writer: WakeRuleWriter): Boolean {
    val prefs = context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE)
    val generation = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    val fingerprint = "${Build.FINGERPRINT}/$generation/log"
    if (generation >= 0 && prefs.getString("peer_probe_build", null) == fingerprint) return prefs.getBoolean("peer_probe_ok", false)
    val service = WakeFilterProbeService::class.java
    val pkg = context.packageName
    val action = "$pkg.PRIVATE_IFW_PROBE.${UUID.randomUUID()}"
    fun bind(candidate: String): Boolean {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val bound = try {
            context.bindService(Intent(context, service).setAction(candidate), connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            // ContextImpl turns AMS's ordinary bind denial into a local exception.
            // Exceptions thrown inside a framework filter arrive via Parcel and are unsafe.
            if (isLocalBindDenial(e)) return false
            throw e
        }
        if (bound) context.unbindService(connection)
        return bound
    }
    var conclusive = false
    var supported = false
    try {
        val self = WakeServiceFilter(pkg, pkg, service.name, action)
        val differentSender = "blocker.probe.p" + UUID.randomUUID().toString().replace("-", "")
        val alternate = self.copy(caller = differentSender, action = "$action.OTHER_SENDER")
        val actions = setOf(self.action, alternate.action)
        val since = System.currentTimeMillis()
        writer.write(WakeRuleFileKind.PROBE, renderWakeRules(listOf(self, alternate), forcePackageFilter = true, block = false))
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(250)
            if (!bind(alternate.action) || !bind(self.action)) break
            try {
                val matched = writer.matchedProbeActions(since, actions)
                if (alternate.action in matched) {
                    conclusive = true
                    break
                }
                if (self.action !in matched) continue
                // The positive match proves the rules are active. Try the negative control again
                // in case the firewall loaded the file between the two initial calls.
                if (!bind(alternate.action)) break
                supported = alternate.action !in writer.matchedProbeActions(since, actions)
                conclusive = true
                break
            } catch (e: IOException) {
                Timber.w(e, "Cannot read the Intent Firewall probe matches")
                break
            }
        }
    } catch (_: SecurityException) {
        conclusive = true
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { writer.write(WakeRuleFileKind.PROBE, null) }
    }
    if (conclusive && generation >= 0) {
        prefs.edit {
            putString("peer_probe_build", fingerprint)
            putBoolean("peer_probe_ok", supported)
        }
    }
    return supported
}

internal fun isLocalBindDenial(error: SecurityException): Boolean = error.message?.startsWith("Not allowed to bind to service") == true && error.stackTrace.firstOrNull()?.className == "android.app.ContextImpl"
