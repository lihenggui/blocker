// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.merxury.blocker.core.model.wake.WakeObservation
import java.math.BigDecimal

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

    fun snapshot(source: WakeCommand, output: String, now: Long, profile: Int = 0): List<WakeObservation> {
        val facts = mutableSetOf<WakeObservation>()
        var host: String? = null
        var client: String? = null
        var clientUid: Int? = null
        var hostUid: Int? = null
        var pending: MatchResult? = null
        var creatorUid: Int? = null
        var sourceUser = profile
        val meta = mapOf("user" to profile.toString())
        for (line in output.lineSequence()) {
            val text = line.trim()
            when (source) {
                WakeCommand.SERVICES, WakeCommand.PROVIDERS -> {
                    if (text.startsWith("* ServiceRecord{") || text.startsWith("* ContentProviderRecord{")) {
                        host = component.find(text)?.value
                        client = null
                        clientUid = null
                        hostUid = null
                    }
                    if (text.startsWith("uid=")) hostUid = text.substringAfter('=').substringBefore(' ').toIntOrNull()
                    if (text.startsWith("app=ProcessRecord{") || text.startsWith("proc=ProcessRecord{")) {
                        hostUid = process.find(text)?.groupValues?.get(3)?.let(::uidFromDump) ?: hostUid
                    }
                    if (text.startsWith("* Client AppBindRecord{") || text.startsWith("client=ProcessRecord{")) {
                        val match = process.find(text)
                        client = match?.groupValues?.get(2)
                        clientUid = match?.groupValues?.get(3)?.let(::uidFromDump)
                    }
                    if (source == WakeCommand.PROVIDERS && text.startsWith("->")) {
                        val match = connectionClient.find(text)
                        client = match?.groupValues?.get(1)
                        clientUid = match?.groupValues?.get(2)?.let(::uidFromDump)
                    }
                    if (host != null && client != null) {
                        facts += WakeObservation(
                            "HeldConnection",
                            now,
                            callerUid = clientUid,
                            targetUid = hostUid,
                            targetPackage = host.substringBefore('/'),
                            component = host,
                            attributes = mapOf("client_process" to client, "type" to source.name),
                        )
                        client = null
                    }
                }

                WakeCommand.PROCESSES -> {
                    val match = importance.find(text)
                    if (match != null) {
                        client = match.groupValues[2]
                        host = match.groupValues[4]
                    }
                    if (client != null && text.startsWith("oom:")) {
                        val adj = Regex("""\bset=(-?\d+)""").find(text)?.groupValues?.get(1)
                        facts += WakeObservation(
                            "ProcessImportance",
                            now,
                            processName = client,
                            attributes = mapOf("oom_adj" to adj.orEmpty(), "reason" to host.orEmpty()),
                        )
                        client = null
                    }
                }

                WakeCommand.WIDGETS -> {
                    if (text.startsWith("provider=")) {
                        component.find(text)?.value?.let {
                            facts += WakeObservation("WidgetProvider", now, component = it, targetPackage = it.substringBefore('/'))
                        }
                    }
                }

                WakeCommand.ROLES -> {
                    if (text.startsWith("user_id=")) sourceUser = text.substringAfter('=').toIntOrNull() ?: profile
                    if (text.startsWith("name=android.app.role.")) host = text.substringAfter('=')
                    if (text.startsWith("holders=") && host != null) {
                        for (pkg in text.substringAfter('=').trim('[', ']').split(", ")) {
                            if (pkg.matches(Regex("""[\w.]+"""))) facts += WakeObservation("RoleHolder", now, targetPackage = pkg, action = host, attributes = mapOf("user" to sourceUser.toString()))
                        }
                    }
                }

                WakeCommand.INPUT, WakeCommand.LISTENERS, WakeCommand.ACCESSIBILITY, WakeCommand.VPN -> {
                    for (item in text.split(':')) {
                        if (item.matches(Regex("""[a-zA-Z][\w.]+(?:/[\w.$]+)?""")) && item != "null") {
                            facts += WakeObservation(
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

                WakeCommand.JOBS -> {
                    if (text.startsWith("JOB #")) {
                        component.find(text.substringAfter(": "))?.value?.let {
                            facts += WakeObservation(
                                "ScheduledJobSnapshot",
                                now,
                                component = it,
                                targetPackage = it.substringBefore('/'),
                                attributes = mapOf("job" to text.substringAfter('#').substringBefore(':').take(64)),
                            )
                        }
                    }
                }

                WakeCommand.INTENTS -> {
                    if ("PendingIntentRecord{" in text && text.startsWith("#")) {
                        pending = pendingRecord.find(text)
                        creatorUid = null
                    }
                    if (text.startsWith("uid=")) creatorUid = text.substringAfter('=').substringBefore(' ').toIntOrNull()
                    if (text.startsWith("requestIntent=") && pending != null) {
                        val fields = token.findAll(text.substringAfter('=')).associate { it.groupValues[1] to it.groupValues[2] }
                        facts += WakeObservation(
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

                WakeCommand.ALARMS -> {
                    if ("Alarm{" in text) host = Regex("""whenElapsed\s+\d+\s+([\w.]+)}""").find(text)?.groupValues?.get(1)
                    if (text.startsWith("operation=") && host != null) {
                        val operation = pendingRecord.find(text)
                        facts += WakeObservation(
                            "PendingAlarm",
                            now,
                            callerPackage = host,
                            attributes = operation?.let { mapOf("record" to it.groupValues[1], "creator_package" to it.groupValues[2]) }.orEmpty(),
                        )
                        host = null
                    }
                }

                WakeCommand.COMPANIONS -> {
                    if ("Association" in text) {
                        Regex("""(?:packageName|package)=([\w.]+)""").find(text)?.let {
                            facts += WakeObservation("CompanionAssociation", now, targetPackage = it.groupValues[1])
                        }
                    }
                }

                WakeCommand.WALLPAPER -> {
                    if ("mWallpaperComponent=" in text) {
                        component.find(text.substringAfter("mWallpaperComponent="))?.let {
                            facts += WakeObservation("WallpaperBinding", now, component = it.value, targetPackage = it.value.substringBefore('/'))
                        }
                    }
                }

                WakeCommand.SYNCS -> if ("SyncAdapterType" in text) {
                    Regex("""packageName[=: ]+([\w.]+)""").find(text)?.groupValues?.get(1)?.let {
                        facts += WakeObservation("SyncAdapter", now, targetPackage = it)
                    }
                }

                WakeCommand.BATTERY -> {
                    val values = text.split(',')
                    if (values.getOrNull(3) == "pr" && values.size >= 7) {
                        val uid = values[1].toIntOrNull() ?: continue
                        val proc = values[4].trim('"')
                        if (proc.matches(Regex("""[\w.:]+"""))) {
                            facts += WakeObservation(
                                "ProcessCpuSnapshot",
                                now,
                                targetUid = uid,
                                processName = proc,
                                attributes = mapOf("user_ms" to values[5], "system_ms" to values[6]),
                            )
                        }
                    }
                }

                else -> Unit
            }
        }
        return facts.toList()
    }
}

data class WakePackageUid(val packageName: String, val uid: Int, val version: Long?, val apkPath: String? = null)
