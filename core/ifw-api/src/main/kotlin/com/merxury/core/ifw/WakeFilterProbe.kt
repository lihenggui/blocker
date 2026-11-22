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

class WakeFilterProbeService : Service() {
    override fun onBind(intent: Intent): IBinder = Binder()
}

/** Some framework builds throw a cross-user SecurityException back into an ordinary caller. */
internal suspend fun testPeerFilter(context: Context, writer: WakeRuleWriter): Boolean {
    val prefs = context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE)
    val generation = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    val fingerprint = "${Build.FINGERPRINT}/$generation"
    if (generation >= 0 && prefs.getString("peer_probe_build", null) == fingerprint) return prefs.getBoolean("peer_probe_ok", false)
    val service = WakeFilterProbeService::class.java
    val pkg = context.packageName
    val action = "$pkg.PRIVATE_IFW_PROBE"
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
        val differentSender = "blocker.probe.p" + java.util.UUID.randomUUID().toString().replace("-", "")
        val alternate = self.copy(caller = differentSender, action = "$action.OTHER_SENDER")
        val xml = renderWakeRules(listOf(self, alternate), forcePackageFilter = true)
        writer.write(xml, true)
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(250)
            if (!bind(action)) {
                supported = bind("$action.PASS") && bind(alternate.action)
                conclusive = true
                break
            }
        }
    } catch (_: SecurityException) {
        conclusive = true
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { writer.write(null, true) }
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
