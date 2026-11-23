// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.merxury.blocker.core.model.wake.WakeObservation
import java.math.BigDecimal

/** One snapshot of the apps that may not show notifications: attribute "apps" lists them as `package/uid`. */
const val WAKE_NOTIFICATIONS_OFF = "NotificationsOff"

internal object WakeSystemEvidence {
    private val logLine = Regex("""^\s*(\d+\.\d+)\s+(?:1000|system)\s+\d+\s+\d+\s+I\s+(am_proc_start|am_proc_died|am_kill|am_schedule_service_restart)\s*:\s*\[(.*)]\s*$""")
    private val component = Regex("""([a-zA-Z][\w.$]+/[\w.$]+)""")
    private val process = Regex("""ProcessRecord\{\S+\s+(\d+):([^/\s]+)/([^}\s]+)""")
    private val importance = Regex("""(?:Proc|PERS)\s+#\s*\d+:.*?\s(\d+):([^/\s]+)/([^\s]+)\s+\(([^)]+)\)""")
    private val connectionClient = Regex("""^->\s+\d+:([^/\s]+)/([^\s]+)""")
    private val pendingRecord = Regex("""PendingIntentRecord\{([a-fA-F0-9]+)\s+([\w.]+)\s+(\w+)""")
    private val token = Regex("""(?:^|\s)(act|pkg|cmp)=([^\s}]+)""")
    private val user = Regex("""^UserInfo\{(\d+):""")
    private val installed = Regex("""package:([^\s]+).*?uid:(\d+)""")
    private val version = Regex("""versionCode:(\d+)""")

    private fun uidFromDump(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        val match = Regex("""u(\d+)a(\d+)""").matchEntire(value) ?: return null
        return match.groupValues[1].toInt() * 100000 + 10000 + match.groupValues[2].toInt()
    }

    fun users(output: String): List<Int> {
        val profiles = mutableSetOf<Int>()
        for (line in output.lineSequence().map(String::trim)) {
            if (line.isEmpty() || line == "Users:") continue
            // A partially recognized user list cannot establish that owner is the only profile.
            profiles += user.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return emptyList()
        }
        return profiles.toList()
    }

    fun packages(output: String): List<WakePackageUid> = output.lineSequence().mapNotNull { line ->
        val match = installed.find(line) ?: return@mapNotNull null
        val argument = match.groupValues[1]
        val pkg = argument.substringAfterLast('=')
        val path = argument.removeSuffix("=$pkg").takeIf { it.startsWith("/") && it.endsWith(".apk") }
        WakePackageUid(pkg, match.groupValues[2].toInt(), version.find(line)?.groupValues?.get(1)?.toLongOrNull(), path)
    }.toList()

    fun logcat(output: String, wallMillis: Long, elapsedNanos: Long): List<WakeObservation> = output.lineSequence().mapNotNull { line ->
        val groups = logLine.matchEntire(line)?.groupValues ?: return@mapNotNull null
        val eventWall = try {
            BigDecimal(groups[1]).movePointRight(9).longValueExact()
        } catch (_: ArithmeticException) {
            return@mapNotNull null
        }
        val age = wallMillis * 1_000_000 - eventWall
        if (age !in 0 until elapsedNanos) return@mapNotNull null
        val elapsed = elapsedNanos - age
        val fields = groups[3].split(',')
        fun field(index: Int) = fields.getOrNull(index)?.trim()
        val profile = field(0)?.toIntOrNull() ?: return@mapNotNull null
        when (groups[2]) {
            "am_proc_start" -> WakeObservation(
                groups[2],
                elapsed,
                targetUid = field(2)?.toIntOrNull(),
                pid = field(1)?.toIntOrNull(),
                processName = field(3),
                component = field(5),
                attributes = mapOf("hosting_type" to field(4).orEmpty()),
            )

            "am_schedule_service_restart" -> WakeObservation(
                groups[2],
                elapsed,
                component = field(1),
                attributes = mapOf("user" to profile.toString(), "delay_ms" to field(2).orEmpty()),
            )

            else -> WakeObservation(
                groups[2],
                elapsed,
                pid = field(1)?.toIntOrNull(),
                processName = field(2),
                attributes = mapOf("user" to profile.toString(), "oom_adj" to field(3).orEmpty(), "reason" to field(4).orEmpty()),
            )
        }.copy(eventWallNanos = eventWall)
    }.toList()

    /** Wall millis of the oldest entry left in the event buffer, or null if it is empty. */
    fun eventLogHead(output: String): Long? = output.lineSequence().firstNotNullOfOrNull { line ->
        Regex("""^\s*(\d+)\.(\d{3})\d*\s""").find(line)?.destructured?.let { (seconds, millis) -> seconds.toLong() * 1000 + millis.toLong() }
    }

    /**
     * A gap when the event buffer no longer reaches back to [since], allowing [tolerance] millis, or
     * when its start could not be read.
     */
    fun eventLogGap(since: Long, head: String?, tolerance: Long = 0): String? {
        if (head == null) return "event log start unknown"
        val oldest = eventLogHead(head) ?: return null
        return if (oldest > since + tolerance) "event log wrapped after resume point" else null
    }

    /** `dumpsys package protected-broadcasts` lists one action per indented line after a header. */
    fun protectedBroadcasts(output: String): Set<String> = output.lineSequence()
        .filter { it.startsWith("  ") }.map(String::trim).filter { it.isNotEmpty() && ' ' !in it }.toSet()

    /** `dumpsys deviceidle whitelist` prints `type,package,uid` for system-excidle, system and user entries. */
    fun idleWhitelist(output: String): Map<String, Set<String>> = output.lineSequence().mapNotNull { line ->
        val fields = line.trim().split(',')
        if (fields.size == 3 && fields[0] in setOf("system-excidle", "system", "user") && fields[2].toIntOrNull() != null) fields[1] to fields[0] else null
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    /** `am get-standby-bucket` without a package prints `package: bucket` for each package. */
    fun standbyBuckets(output: String): Map<String, Int> = output.lineSequence().mapNotNull { line ->
        val match = Regex("""^([\w.]+): (\d+)$""").find(line.trim()) ?: return@mapNotNull null
        match.groupValues[1] to match.groupValues[2].toInt()
    }.toMap()

    /** `cmd appops query-op` prints one package per line, or "No operations.". */
    fun queriedPackages(output: String): Set<String> = output.lineSequence().map(String::trim)
        .filter { it.matches(Regex("""[a-zA-Z][\w]*(?:\.[\w]+)+""")) }.toSet()

    private fun heldConnections(source: WakeCommand, output: String, now: Long): List<WakeObservation> {
        val facts = mutableSetOf<WakeObservation>()
        var host: String? = null
        var hostUid: Int? = null
        var hostProcess: String? = null
        var bindingIndent = -1
        var action: String? = null
        for (line in output.lineSequence()) {
            val text = line.trim()
            if (text.isEmpty()) continue
            val indent = line.indexOfFirst { !it.isWhitespace() }
            if (indent <= bindingIndent) {
                bindingIndent = -1
                action = null
            }
            when {
                text.startsWith("* ServiceRecord{") || text.startsWith("* ContentProviderRecord{") -> {
                    host = component.find(text)?.value
                    hostUid = null
                    hostProcess = null
                }

                text.startsWith("uid=") -> hostUid = text.substringAfter('=').substringBefore(' ').toIntOrNull()

                text.startsWith("app=ProcessRecord{") || text.startsWith("proc=ProcessRecord{") -> process.find(text)?.let { match ->
                    hostUid = uidFromDump(match.groupValues[3]) ?: hostUid
                    hostProcess = match.groupValues[2]
                }

                source == WakeCommand.SERVICES && text.startsWith("* IntentBindRecord{") -> bindingIndent = indent

                bindingIndent >= 0 && text.startsWith("intent={") -> {
                    // ActivityManager prints the action first inside this binding's own intent.
                    action = text.takeIf { it.startsWith("intent={act=") }?.substringAfter("intent={act=")
                        ?.substringBefore(' ')?.substringBefore('}')?.let(::wakeAction)
                }

                else -> {
                    val (client, uid) = when {
                        text.startsWith("* Client AppBindRecord{") || text.startsWith("client=ProcessRecord{") ->
                            process.find(text)?.destructured?.let { (_, name, uid) -> name to uid }

                        source == WakeCommand.PROVIDERS && text.startsWith("->") ->
                            connectionClient.find(text)?.destructured?.let { (name, uid) -> name to uid }

                        else -> null
                    } ?: continue
                    val target = host ?: continue
                    facts += WakeObservation(
                        "HeldConnection",
                        now,
                        callerUid = uidFromDump(uid),
                        targetUid = hostUid,
                        targetPackage = target.substringBefore('/'),
                        component = target,
                        action = action.takeIf { text.startsWith("* Client AppBindRecord{") },
                        // The hosting process tells which of the app's processes a binding keeps.
                        attributes = mapOf("client_process" to client, "type" to source.name) + listOfNotNull(hostProcess?.let { "host_process" to it }),
                    )
                }
            }
        }
        return facts.toList()
    }

    fun snapshot(source: WakeCommand, output: String, now: Long, profile: Int = 0, sdk: Int = android.os.Build.VERSION.SDK_INT): List<WakeObservation> {
        val lines = output.lineSequence().map(String::trim)
        return when (source) {
            WakeCommand.SERVICES, WakeCommand.PROVIDERS -> heldConnections(source, output, now)
            WakeCommand.PROCESSES -> processImportance(lines, now)
            WakeCommand.NOTIFICATIONS -> notificationsOff(lines, now, sdk)
            WakeCommand.WIDGETS -> widgetProviders(lines, now)
            WakeCommand.ROLES -> roleHolders(lines, now, profile)
            WakeCommand.INPUT, WakeCommand.LISTENERS, WakeCommand.ACCESSIBILITY, WakeCommand.VPN -> systemBindings(lines, now, source, profile)
            WakeCommand.JOBS -> scheduledJobs(lines, now)
            WakeCommand.INTENTS -> pendingIntents(lines, now)
            WakeCommand.ALARMS -> pendingAlarms(lines, now)
            WakeCommand.COMPANIONS -> companionAssociations(lines, now)
            WakeCommand.WALLPAPER -> wallpaperBindings(lines, now)
            WakeCommand.SYNCS -> syncAdapters(lines, now)
            WakeCommand.BATTERY -> processCpu(lines, now)
            else -> emptyList()
        }.distinct()
    }

    // "AppSettings: example.app (10123) importance=NONE userSet=false": the user turned the app's
    // notifications off or, from Android 13, never allowed them. Android 13 and later print the
    // importance only for an app that requests the permission; one that does not cannot notify either.
    private val notificationSettings = Regex("""^AppSettings: (\S+) \((\d+)\)(?: |$)(?:.*?\bimportance=(\w+))?""")
    private const val NOTIFICATION_PERMISSION_SDK = 33

    // One record per snapshot keeps the history small, and a snapshot without such an app is still seen.
    private fun notificationsOff(lines: Sequence<String>, now: Long, sdk: Int): List<WakeObservation> {
        val settings = lines.mapNotNull(notificationSettings::find).toList()
        if (settings.isEmpty()) return emptyList()
        // The permission belongs to the uid: a package that shares the uid of one allowed to notify is allowed too.
        val allowed = settings.filter { it.groupValues[3].isNotEmpty() && it.groupValues[3] != "NONE" }.map { it.groupValues[2] }.toSet()
        val off = settings.filter { it.groupValues[3] == "NONE" || (sdk >= NOTIFICATION_PERMISSION_SDK && it.groupValues[3].isEmpty() && it.groupValues[2] !in allowed) }
        return listOf(WakeObservation(WAKE_NOTIFICATIONS_OFF, now, attributes = mapOf("apps" to off.joinToString(",") { "${it.groupValues[1]}/${it.groupValues[2]}" })))
    }

    private fun processImportance(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        var host: String? = null
        var client: String? = null
        for (text in lines) {
            val match = importance.find(text)
            if (match != null) {
                client = match.groupValues[2]
                host = match.groupValues[4]
            }
            if (client != null && text.startsWith("oom:")) {
                val adj = Regex("""\bset=(-?\d+)""").find(text)?.groupValues?.get(1)
                this += WakeObservation(
                    "ProcessImportance",
                    now,
                    processName = client,
                    attributes = mapOf("oom_adj" to adj.orEmpty(), "reason" to host.orEmpty()),
                )
                client = null
            }
        }
    }

    private fun widgetProviders(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            if (text.startsWith("provider=")) {
                component.find(text)?.value?.let {
                    this += WakeObservation("WidgetProvider", now, component = it, targetPackage = it.substringBefore('/'))
                }
            }
        }
    }

    private fun roleHolders(lines: Sequence<String>, now: Long, profile: Int): List<WakeObservation> = buildList {
        var host: String? = null
        var sourceUser = profile
        for (text in lines) {
            if (text.startsWith("user_id=")) sourceUser = text.substringAfter('=').toIntOrNull() ?: profile
            if (text.startsWith("name=android.app.role.")) host = text.substringAfter('=')
            if (text.startsWith("holders=") && host != null) {
                for (pkg in text.substringAfter('=').trim('[', ']').split(", ")) {
                    if (pkg.matches(Regex("""[\w.]+"""))) this += WakeObservation("RoleHolder", now, targetPackage = pkg, action = host, attributes = mapOf("user" to sourceUser.toString()))
                }
            }
        }
    }

    private fun systemBindings(lines: Sequence<String>, now: Long, source: WakeCommand, profile: Int): List<WakeObservation> = buildList {
        val meta = mapOf("user" to profile.toString())
        for (text in lines) {
            for (item in text.split(':')) {
                if (item.matches(Regex("""[a-zA-Z][\w.]+(?:/[\w.$]+)?""")) && item != "null") {
                    this += WakeObservation(
                        "SystemBinding",
                        now,
                        targetPackage = item.substringBefore('/'),
                        component = item,
                        action = source.name,
                        attributes = meta,
                    )
                }
            }
        }
    }

    private fun scheduledJobs(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            if (text.startsWith("JOB #")) {
                component.find(text.substringAfter(": "))?.value?.let {
                    this += WakeObservation(
                        "ScheduledJobSnapshot",
                        now,
                        component = it,
                        targetPackage = it.substringBefore('/'),
                        attributes = mapOf("job" to text.substringAfter('#').substringBefore(':').take(64)),
                    )
                }
            }
        }
    }

    private fun pendingIntents(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        var pending: MatchResult? = null
        var creatorUid: Int? = null
        for (text in lines) {
            if ("PendingIntentRecord{" in text && text.startsWith("#")) {
                pending = pendingRecord.find(text)
                creatorUid = null
            }
            if (text.startsWith("uid=")) creatorUid = text.substringAfter('=').substringBefore(' ').toIntOrNull()
            if (text.startsWith("requestIntent=") && pending != null) {
                val fields = token.findAll(text.substringAfter('=')).associate { it.groupValues[1] to it.groupValues[2] }
                this += WakeObservation(
                    "PendingIntentRegistration",
                    now,
                    callerUid = creatorUid,
                    callerPackage = pending.groupValues[2],
                    targetPackage = fields["pkg"] ?: fields["cmp"]?.substringBefore('/'),
                    component = fields["cmp"],
                    action = wakeAction(fields["act"]),
                    attributes = mapOf("record" to pending.groupValues[1], "type" to pending.groupValues[3]),
                )
                pending = null
            }
        }
    }

    private fun pendingAlarms(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        var host: String? = null
        for (text in lines) {
            // ICU, which backs java.util.regex on Android, rejects an unescaped closing brace.
            if ("Alarm{" in text) host = Regex("""whenElapsed\s+\d+\s+([\w.]+)\}""").find(text)?.groupValues?.get(1)
            if (text.startsWith("operation=") && host != null) {
                val operation = pendingRecord.find(text)
                this += WakeObservation(
                    "PendingAlarm",
                    now,
                    callerPackage = host,
                    attributes = operation?.let { mapOf("record" to it.groupValues[1], "creator_package" to it.groupValues[2]) }.orEmpty(),
                )
                host = null
            }
        }
    }

    private fun companionAssociations(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            if ("Association" in text) {
                Regex("""(?:packageName|package)=([\w.]+)""").find(text)?.let {
                    this += WakeObservation("CompanionAssociation", now, targetPackage = it.groupValues[1])
                }
            }
        }
    }

    private fun wallpaperBindings(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            if ("mWallpaperComponent=" in text) {
                component.find(text.substringAfter("mWallpaperComponent="))?.let {
                    this += WakeObservation("WallpaperBinding", now, component = it.value, targetPackage = it.value.substringBefore('/'))
                }
            }
        }
    }

    private fun syncAdapters(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            if ("SyncAdapterType" in text) {
                Regex("""packageName[=: ]+([\w.]+)""").find(text)?.groupValues?.get(1)?.let {
                    this += WakeObservation("SyncAdapter", now, targetPackage = it)
                }
            }
        }
    }

    private fun processCpu(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            val values = text.split(',')
            if (values.getOrNull(3) == "pr" && values.size >= 7) {
                val uid = values[1].toIntOrNull() ?: continue
                val proc = values[4].trim('"')
                if (proc.matches(Regex("""[\w.:]+"""))) {
                    this += WakeObservation(
                        "ProcessCpuSnapshot",
                        now,
                        targetUid = uid,
                        processName = proc,
                        attributes = mapOf("user_ms" to values[5], "system_ms" to values[6]),
                    )
                }
            }
        }
    }
}

data class WakePackageUid(val packageName: String, val uid: Int, val version: Long?, val apkPath: String? = null)
