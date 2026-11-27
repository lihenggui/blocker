// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.os.Build
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The fixed command lines the wake feature reads evidence with, run by `ReadWakeSource`. */
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
    OVERLAYS(listOf("dumpsys", "-t", "5", "window", "windows")),
    AUDIO(listOf("dumpsys", "-t", "5", "audio")),
    USERS(listOf("pm", "list", "users")),
    PACKAGES(listOf("pm", "list", "packages", "-f", "-U")),
    INTENTS(listOf("dumpsys", "-t", "5", "activity", "intents")),
    COMPANIONS(listOf("dumpsys", "-t", "5", "companiondevice")),
    WALLPAPER(listOf("dumpsys", "-t", "5", "wallpaper")),
    DEVICE_POLICY(listOf("dumpsys", "-t", "5", "device_policy")),
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

    fun arguments(profile: Int, appUid: Int, boot: Int, argument: Long = 0, sdk: Int = Build.VERSION.SDK_INT): List<String> {
        require(profile >= 0)
        val id = WakeStatsCodec.configId(appUid, boot)
        return when (this) {
            PACKAGES -> {
                val versionCode = if (sdk >= Build.VERSION_CODES.O) listOf("--show-versioncode") else emptyList()
                args + versionCode + listOf("--user", profile.toString())
            }

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
     * The process to start: [arguments] behind a shell that drops stderr. Only constant argv and
     * validated integers reach it, and it never goes through `su`: it runs in a process that is
     * privileged already.
     */
    fun processCommand(profile: Int, appUid: Int, boot: Int, argument: Long = 0): List<String> {
        // Keep stderr out of binary statsd reports on every supported API, including Android 7.
        // The shell only redirects descriptors. Each argument is passed through unchanged.
        return listOf("/system/bin/sh", "-c", """exec "$@" 2>/dev/null""", "blocker") + arguments(profile, appUid, boot, argument)
    }

    /**
     * Runs [processCommand] and writes at most 12 MiB of its stdout to [output], as
     * [runBounded] does. [INSTALL] feeds the statsd config on stdin, owned by this process's uid.
     *
     * @throws IOException when the command fails, is stopped or prints more
     */
    fun execute(profile: Int, appUid: Int, boot: Int, argument: Long, output: OutputStream) {
        val input = if (this == INSTALL) WakeStatsCodec.config(WakeStatsCodec.configId(appUid, boot), android.os.Process.myUid()) else null
        runBounded(ProcessBuilder(processCommand(profile, appUid, boot, argument)).start(), name, input, output)
    }
}

private val timer = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "WakeCommandTimeout").apply { isDaemon = true }
}

/** Sends SIGKILL: `destroy` does so before Android 8, which has no `destroyForcibly`. */
private fun Process.killNow() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) destroyForcibly() else destroy()
}

/**
 * Feeds [input] to [process], copies at most 12 MiB of its stdout to [output] and waits for it to
 * exit. After [limitMillis] it is asked to stop, and [graceMillis] later killed with [kill], so a
 * command that ignores the request, or keeps stdout open, cannot hold the call. Should even the
 * kill not end it, its stdout is closed and the wait abandoned another two [graceMillis] later.
 *
 * The wait itself blocks instead of polling: `Process.waitFor` with a timeout sleeps up to 100 ms
 * between checks on Android, which a collection of 30 commands would pay 30 times.
 *
 * @throws IOException naming [name] when the command fails, runs out of time or prints more
 */
internal fun runBounded(
    process: Process,
    name: String,
    input: ByteArray?,
    output: OutputStream,
    limitMillis: Long = 12_000,
    graceMillis: Long = 1_000,
    kill: (Process) -> Unit = Process::killNow,
) {
    val timedOut = AtomicBoolean()
    val caller = Thread.currentThread()
    val interruptLock = Any()
    var finished = false
    var interruptedByTimer = false
    var interruptedExternally = false
    val stop = timer.schedule({
        timedOut.set(true)
        process.destroy()
    }, limitMillis, TimeUnit.MILLISECONDS)
    val killer = timer.schedule({ kill(process) }, limitMillis + graceMillis, TimeUnit.MILLISECONDS)
    val abandon = timer.schedule({
        // A descendant that survived the kill may still hold stdout open; closing it ends the read.
        try {
            process.inputStream.close()
        } catch (e: IOException) {
            Timber.w(e, "Cannot close the output of $name")
        }
        synchronized(interruptLock) {
            if (!finished) {
                interruptedByTimer = true
                caller.interrupt()
            }
        }
    }, limitMillis + 3 * graceMillis, TimeUnit.MILLISECONDS)
    try {
        process.outputStream.use { if (input != null) it.write(input) }
        process.inputStream.use { it.copyBounded(output) }
        val status = process.waitFor()
        if (timedOut.get()) throw IOException("$name did not finish within ${limitMillis / 1000.0} s")
        if (status != 0) throw IOException("$name exited with status $status")
    } catch (e: InterruptedException) {
        if (synchronized(interruptLock) { interruptedByTimer }) throw IOException("$name did not exit after it was killed", e)
        interruptedExternally = true
        throw e
    } finally {
        synchronized(interruptLock) {
            finished = true
            // Only consume an interrupt sent by this invocation's timer.
            if (interruptedByTimer) Thread.interrupted()
        }
        abandon.cancel(false)
        stop.cancel(false)
        killer.cancel(false)
        try {
            kill(process)
        } finally {
            if (interruptedExternally) caller.interrupt()
        }
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
