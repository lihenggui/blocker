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

    // logcat applies -t before tag filters. Resume by time instead: {since} is wall seconds.millis.
    EVENTS(
        listOf(
            "logcat", "-b", "events", "-d", "-T", "{since}", "-v", "threadtime", "-v", "epoch", "-v", "uid",
            "am_proc_start:I", "am_proc_died:I", "am_kill:I", "am_schedule_service_restart:I", "*:S",
        ),
    ),

    // The oldest retained entry of the whole buffer shows whether it wrapped past the resume point.
    EVENTS_HEAD(listOf("logcat", "-b", "events", "-d", "-m", "1", "-v", "threadtime", "-v", "epoch", "-v", "uid")),
    PROCSTATS(listOf("dumpsys", "-t", "10", "procstats", "-c", "--current")),

    // Committed files are listed oldest first. A too large index only reports their count.
    PROCSTATS_COUNT(listOf("dumpsys", "-t", "5", "procstats", "--last", "$PROCSTATS_COUNT_PROBE")),
    PROCSTATS_LAST(listOf("dumpsys", "-t", "10", "procstats", "-c", "--last", "{index}")),
    START_INFO(listOf("dumpsys", "-t", "10", "activity", "start-info")),
    PROTECTED_BROADCASTS(listOf("dumpsys", "-t", "5", "package", "protected-broadcasts")),
    IDLE_WHITELIST(listOf("dumpsys", "-t", "5", "deviceidle", "whitelist")),
    STANDBY_BUCKETS(listOf("am", "get-standby-bucket", "--user", "{profile}")),
    BACKGROUND_RESTRICTED(listOf("cmd", "appops", "query-op", "--user", "{profile}", "RUN_ANY_IN_BACKGROUND", "ignore")),
    SERVICES(listOf("dumpsys", "-t", "5", "activity", "services")),
    PROVIDERS(listOf("dumpsys", "-t", "5", "activity", "providers")),
    PROCESSES(listOf("dumpsys", "-t", "5", "activity", "oom")),
    NOTIFICATIONS(listOf("dumpsys", "-t", "5", "notification")),
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

    fun arguments(profile: Int, appUid: Int, boot: Int, argument: Long = 0): List<String> {
        require(profile >= 0)
        val id = WakeStatsCodec.configId(appUid, boot)
        return when (this) {
            PACKAGES -> args + profile.toString()

            INPUT, ACCESSIBILITY, LISTENERS, VPN -> listOf("settings", "--user", profile.toString()) + args.drop(1)

            RENEW -> args.map { if (it == "{label}") WakeStatsCodec.leaseLabel(id).toString() else it }

            EVENTS -> {
                require(argument >= 0)
                val since = "${argument / 1000}.${(argument % 1000).toString().padStart(3, '0')}"
                args.map { if (it == "{since}") since else it }
            }

            PROCSTATS_LAST -> {
                require(argument in 1 until PROCSTATS_COUNT_PROBE)
                args.map { if (it == "{index}") argument.toString() else it }
            }

            STANDBY_BUCKETS, BACKGROUND_RESTRICTED -> args.map { if (it == "{profile}") profile.toString() else it }

            else -> args.map { if (it == "{config}") id.toString() else it }
        }
    }

    /**
     * The process to start: [arguments] behind a shell that drops stderr, through `su` when
     * [root] asks this process to gain root first. A process that already runs privileged passes
     * false.
     */
    fun processCommand(profile: Int, root: Boolean, appUid: Int, boot: Int, argument: Long = 0): List<String> {
        val command = arguments(profile, appUid, boot, argument)
        // Only constant argv and a validated integer reach su. Never accept shell text over Binder.
        val argv = if (root) listOf("su", "-c", command.joinToString(" ") { "'$it'" }) else command
        // Keep stderr out of binary statsd reports on every supported API, including Android 7.
        // The shell only redirects descriptors. Each argument is passed through unchanged.
        return listOf("/system/bin/sh", "-c", """exec "$@" 2>/dev/null""", "blocker") + argv
    }

    /**
     * Runs [processCommand], writes at most 12 MiB of its stdout to [output] and kills it after
     * 12 seconds. [INSTALL] feeds the statsd config on stdin, owned by uid 0 through `su` and by
     * this process's uid otherwise.
     *
     * @throws IOException when the command fails, is killed or prints more
     */
    fun execute(profile: Int, root: Boolean, appUid: Int, boot: Int, argument: Long, output: OutputStream) {
        val id = WakeStatsCodec.configId(appUid, boot)
        val process = ProcessBuilder(processCommand(profile, root, appUid, boot, argument)).start()
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

internal const val PROCSTATS_COUNT_PROBE = 1000

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

// Shizuku replaces a running user service only when this changes: raise it with each change of IWakeReader.
private const val WAKE_USER_SERVICE_VERSION = 6

/** Loaded by Shizuku in its authorized shell/root user service process. */
class WakeUserService @JvmOverloads constructor(private val context: Context? = null) : IWakeReader.Stub() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun read(operation: String, profile: Int, boot: Int, argument: Long): ParcelFileDescriptor {
        val appUid = android.os.Binder.getCallingUid()
        if (context != null) check(appUid == context.applicationInfo.uid) { "Blocker caller required" }
        val command = WakeCommand.valueOf(operation)
        val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
        worker.execute {
            val stream = ParcelFileDescriptor.AutoCloseOutputStream(writer)
            try {
                command.execute(profile, false, appUid, boot, argument, stream)
                stream.close()
            } catch (e: Exception) {
                writer.closeWithError(e.message ?: command.name)
            }
        }
        return reader
    }

    override fun destroy() = kotlin.system.exitProcess(0)
}

internal class WakeSession(val uid: Int, private val remote: IWakeReader?, val generation: Int, private val binderOnly: Boolean = false) {
    val supportsRecorder get() = !binderOnly

    fun read(command: WakeCommand, profile: Int = 0, boot: Int = generation, argument: Long = 0): ByteArray {
        if (binderOnly) return WakeBinderReader.read(command, profile, android.os.Process.myUid(), boot, argument)
        val bytes = java.io.ByteArrayOutputStream()
        if (remote == null) {
            command.execute(profile, true, android.os.Process.myUid(), boot, argument, bytes)
        } else {
            val fd = remote.read(command.name, profile, boot, argument)
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
            .daemon(false).processNameSuffix("wake_reader").tag("wake-reader-${UUID.randomUUID()}").version(WAKE_USER_SERVICE_VERSION)
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
