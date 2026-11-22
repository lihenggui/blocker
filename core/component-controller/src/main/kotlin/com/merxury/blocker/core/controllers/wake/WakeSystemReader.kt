// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.merxury.blocker.core.model.wake.WakeAccess
import com.merxury.blocker.core.utils.LibrootRootAvailabilityChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

internal enum class WakeCommand(val args: List<String>) {
    INSTALL(listOf("cmd", "stats", "config", "update", "{config}")),
    RENEW(listOf("cmd", "stats", "log-app-breadcrumb", "{label}", "1")),
    REPORT(listOf("cmd", "stats", "dump-report", "{config}", "--keep_data", "--include_current_bucket", "--proto")),
    DRAIN(listOf("cmd", "stats", "dump-report", "{config}", "--include_current_bucket", "--proto")),
    REMOVE(listOf("cmd", "stats", "config", "remove", "{config}")),
    EVENTS(
        listOf(
            "logcat", "-b", "events", "-d", "-t", "4096", "-v", "threadtime", "-v", "epoch", "-v", "uid",
            "am_proc_start:I", "am_proc_died:I", "am_kill:I", "am_schedule_service_restart:I", "*:S",
        ),
    ),
    SERVICES(listOf("dumpsys", "-t", "5", "activity", "services")),
    PROVIDERS(listOf("dumpsys", "-t", "5", "activity", "providers")),
    PROCESSES(listOf("dumpsys", "-t", "5", "activity", "oom")),
    USERS(listOf("pm", "list", "users")),
    PACKAGES(listOf("pm", "list", "packages", "-f", "-U", "--show-versioncode", "--user")),
    INTENTS(listOf("dumpsys", "-t", "5", "activity", "intents")),
    COMPANIONS(listOf("dumpsys", "-t", "5", "companiondevice")),
    WALLPAPER(listOf("dumpsys", "-t", "5", "wallpaper")),
    ROLES(listOf("dumpsys", "-t", "5", "role")),
    WIDGETS(listOf("dumpsys", "-t", "5", "appwidget")),
    INPUT(listOf("settings", "get", "secure", "default_input_method")),
    ACCESSIBILITY(listOf("settings", "get", "secure", "enabled_accessibility_services")),
    LISTENERS(listOf("settings", "get", "secure", "enabled_notification_listeners")),
    VPN(listOf("settings", "get", "secure", "always_on_vpn_app")),
    JOBS(listOf("dumpsys", "-t", "5", "jobscheduler")),
    ALARMS(listOf("dumpsys", "-t", "5", "alarm")),
    SYNCS(listOf("dumpsys", "-t", "5", "content")),
    BATTERY(listOf("dumpsys", "-t", "5", "batterystats", "--charged", "--checkin")),
    ;

    fun arguments(profile: Int, appUid: Int, boot: Int): List<String> {
        require(profile >= 0)
        val id = WakeStatsCodec.configId(appUid, boot)
        return when (this) {
            PACKAGES -> args + profile.toString()
            INPUT, ACCESSIBILITY, LISTENERS, VPN -> listOf("settings", "--user", profile.toString()) + args.drop(1)
            RENEW -> args.map { if (it == "{label}") WakeStatsCodec.leaseLabel(id).toString() else it }
            else -> args.map { if (it == "{config}") id.toString() else it }
        }
    }

    fun execute(profile: Int, root: Boolean, appUid: Int, boot: Int, output: OutputStream) {
        val command = arguments(profile, appUid, boot)
        val id = WakeStatsCodec.configId(appUid, boot)
        // Only constant argv and a validated integer reach su. Never accept shell text over Binder.
        val argv = if (root) listOf("su", "-c", command.joinToString(" ") { "'$it'" }) else command
        // Keep stderr out of binary statsd reports on every supported API, including Android 7.
        // The shell only redirects descriptors. Each argument is passed through unchanged.
        val process = ProcessBuilder(listOf("/system/bin/sh", "-c", """exec "$@" 2>/dev/null""", "blocker") + argv).start()
        val timeout = timer.schedule({ process.destroy() }, 12, TimeUnit.SECONDS)
        try {
            process.outputStream.use { if (this == INSTALL) it.write(WakeStatsCodec.config(id, if (root) 0 else android.os.Process.myUid())) }
            process.inputStream.use { it.copyBounded(output) }
            if (process.waitFor() != 0) throw IOException("$name unavailable")
        } finally {
            timeout.cancel(false)
            process.destroy()
        }
    }

    companion object {
        private val timer = Executors.newSingleThreadScheduledExecutor()
    }
}

internal fun InputStream.copyBounded(output: OutputStream) {
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > 12 * 1024 * 1024) throw IOException("Report exceeded the capture limit")
        output.write(buffer, 0, count)
    }
}

/** Loaded by Shizuku in its authorized shell/root user service process. */
class WakeUserService @JvmOverloads constructor(private val context: Context? = null) : IWakeReader.Stub() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun read(operation: String, profile: Int, boot: Int): ParcelFileDescriptor {
        val appUid = android.os.Binder.getCallingUid()
        if (context != null) check(appUid == context.applicationInfo.uid) { "Blocker caller required" }
        val command = WakeCommand.valueOf(operation)
        val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
        worker.execute {
            val stream = ParcelFileDescriptor.AutoCloseOutputStream(writer)
            try {
                command.execute(profile, false, appUid, boot, stream)
                stream.close()
            } catch (e: Exception) {
                writer.closeWithError(e.message ?: command.name)
            }
        }
        return reader
    }

    private fun firewallOwner(): String {
        val owner = checkNotNull(context) { "Shizuku 13 or later required" }
        check(android.os.Process.myUid() == 0 && android.os.Binder.getCallingUid() == owner.applicationInfo.uid)
        return owner.packageName
    }

    override fun writeManagedFirewall(xml: String?, probe: Boolean) = com.merxury.core.ifw.writeManagedWakeFile(firewallOwner(), xml, probe)

    override fun resetManagedFirewall(packageName: String?): Boolean = com.merxury.core.ifw.resetManagedWakeFiles(firewallOwner(), packageName)

    override fun componentJournal(replacement: String?): String {
        check(android.os.Process.myUid() == 0)
        val caller = android.os.Binder.getCallingUid()
        val owner = if (context != null) {
            check(caller == context.applicationInfo.uid)
            context.packageName
        } else {
            // Older Shizuku supplies no Context. The caller can only address its own journal.
            check(android.os.UserHandle.getUserHandleForUid(caller) == android.os.UserHandle.getUserHandleForUid(0) && caller >= 10000)
            val identity = android.os.Binder.clearCallingIdentity()
            try {
                checkNotNull(be.mygod.librootkotlinx.systemContext.packageManager.getPackagesForUid(caller)?.singleOrNull())
            } finally {
                android.os.Binder.restoreCallingIdentity(identity)
            }
        }
        return componentJournalFile(owner, replacement)
    }

    override fun destroy() = kotlin.system.exitProcess(0)
}

internal class WakeSession(val uid: Int, private val remote: IWakeReader?, val generation: Int, private val binderOnly: Boolean = false) {
    val supportsRecorder get() = !binderOnly

    fun componentJournal(replacement: String?): String {
        check(uid == 0)
        return checkNotNull(remote) { "Root user service unavailable" }.componentJournal(replacement)
    }

    fun resetFirewall(packageName: String?): Boolean {
        check(uid == 0)
        return checkNotNull(remote) { "Root user service unavailable" }.resetManagedFirewall(packageName)
    }

    fun writeFirewall(xml: String?, probe: Boolean) {
        check(uid == 0)
        checkNotNull(remote) { "Root user service unavailable" }.writeManagedFirewall(xml, probe)
    }

    fun read(command: WakeCommand, profile: Int = 0, boot: Int = generation): ByteArray {
        if (binderOnly) return WakeBinderReader.read(command, profile, android.os.Process.myUid(), boot)
        val bytes = java.io.ByteArrayOutputStream()
        if (remote == null) {
            command.execute(profile, true, android.os.Process.myUid(), boot, bytes)
        } else {
            val fd = remote.read(command.name, profile, boot)
            ParcelFileDescriptor.AutoCloseInputStream(fd).use {
                it.copyBounded(bytes)
                fd.checkError()
            }
        }
        return bytes.toByteArray()
    }
}

fun authorizedShizukuUid(): Int? = try {
    if (Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) Shizuku.getUid() else null
} catch (e: RuntimeException) {
    Timber.w(e, "Cannot read Shizuku authorization")
    null
}

fun hasAuthorizedWakeFirewallService(): Boolean = authorizedShizukuUid() == 0 && Shizuku.getVersion() >= 13

fun observeWakeShizukuAuthorization(onChanged: (Int?) -> Unit) {
    Shizuku.addBinderReceivedListenerSticky { onChanged(authorizedShizukuUid()) }
    Shizuku.addBinderDeadListener { onChanged(null) }
    Shizuku.addRequestPermissionResultListener { _, _ -> onChanged(authorizedShizukuUid()) }
}

class WakeSystemReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val rootChecker: LibrootRootAvailabilityChecker,
) {
    suspend fun access(): WakeAccess {
        val uid = authorizedShizukuUid()
        return when {
            uid == 0 && Shizuku.getVersion() >= 13 -> WakeAccess.SHIZUKU_ROOT
            rootChecker.hasBackgroundRoot() -> WakeAccess.DIRECT_ROOT
            uid == 0 -> WakeAccess.SHIZUKU_LEGACY_ROOT
            uid == 2000 || uid == 1000 -> WakeAccess.SHIZUKU_SHELL
            else -> WakeAccess.NONE
        }
    }

    internal suspend fun <T> withSession(access: WakeAccess, block: suspend (WakeSession) -> T): T = withContext(Dispatchers.IO) {
        val generation = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
        check(access != WakeAccess.NONE)
        if (access == WakeAccess.DIRECT_ROOT) return@withContext block(WakeSession(0, null, generation))
        val uid = checkNotNull(authorizedShizukuUid()) { "Shizuku authorization unavailable" }
        check(access == WakeAccess.SHIZUKU_SHELL || uid == 0)
        val args = Shizuku.UserServiceArgs(ComponentName(context, WakeUserService::class.java))
            .daemon(false).processNameSuffix("wake_reader").tag("wake-reader-${UUID.randomUUID()}").version(4)
        var connection: ServiceConnection? = null
        try {
            val remote = withContext(Dispatchers.Main.immediate) {
                withTimeoutOrNull(35000) {
                    suspendCancellableCoroutine { continuation ->
                        val binderConnection = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                                if (continuation.isActive) continuation.resume(IWakeReader.Stub.asInterface(service))
                            }
                            override fun onServiceDisconnected(name: ComponentName) = Unit
                        }
                        connection = binderConnection
                        Shizuku.bindUserService(args, binderConnection)
                    }
                }
            }
            block(WakeSession(uid, remote, generation, binderOnly = remote == null))
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                connection?.let {
                    // Detach the local callback, then terminate this non-daemon user service.
                    // remove=true alone leaves the callback cached until asynchronous Binder death.
                    for (remove in listOf(false, true)) {
                        try {
                            Shizuku.unbindUserService(args, it, remove)
                        } catch (e: RuntimeException) {
                            Timber.w(e, "Failed to detach Shizuku wake reader (remove=%s)", remove)
                        }
                    }
                }
            }
        }
    }
}
