// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
     * Runs [processCommand], writes at most 12 MiB of its stdout to [output] and kills it after
     * 12 seconds. [INSTALL] feeds the statsd config on stdin, owned by this process's uid.
     *
     * @throws IOException when the command fails, is killed or prints more
     */
    fun execute(profile: Int, appUid: Int, boot: Int, argument: Long, output: OutputStream) {
        val id = WakeStatsCodec.configId(appUid, boot)
        val process = ProcessBuilder(processCommand(profile, appUid, boot, argument)).start()
        val timeout = timer.schedule({ process.destroy() }, 12, TimeUnit.SECONDS)
        try {
            process.outputStream.use { if (this == INSTALL) it.write(WakeStatsCodec.config(id, android.os.Process.myUid())) }
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
