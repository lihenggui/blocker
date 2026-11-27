// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.component.isRegularAppUid
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.model.wake.WakeObservation
import java.math.BigDecimal

/** One snapshot of the apps that may not show notifications: attribute "apps" lists them as `package/uid`. */
const val WAKE_NOTIFICATIONS_OFF = "NotificationsOff"

/** Snapshots of visible overlay packages and app UIDs with started, unmuted audio tracks. */
const val WAKE_OVERLAY_WINDOWS = "OverlayWindow"
const val WAKE_AUDIO_PLAYBACK = "AudioPlayback"
const val WAKE_DEVICE_ADMIN = "DeviceAdmin"

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
        return match.groupValues[1].toInt() * UIDS_PER_USER + 10000 + match.groupValues[2].toInt()
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
        val uid = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        WakePackageUid(pkg, uid, version.find(line)?.groupValues?.get(1)?.toLongOrNull(), path)
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
            WakeCommand.OVERLAYS -> overlayWindows(output, now)
            WakeCommand.AUDIO -> audioPlayback(output, now)
            WakeCommand.WIDGETS -> widgetProviders(lines, now)
            WakeCommand.DEVICE_POLICY -> deviceAdmins(output, now, profile)
            WakeCommand.ROLES -> roleHolders(lines, now, profile)
            WakeCommand.INPUT, WakeCommand.LISTENERS, WakeCommand.ACCESSIBILITY, WakeCommand.VPN -> systemBindings(lines, now, source, profile)
            WakeCommand.JOBS -> scheduledJobs(lines, now)
            WakeCommand.INTENTS -> pendingIntents(lines, now)
            WakeCommand.ALARMS -> pendingAlarms(lines, now)
            WakeCommand.COMPANIONS -> companionAssociations(lines, now)
            WakeCommand.WALLPAPER -> wallpaperBindings(output, now)
            WakeCommand.SYNCS -> syncAdapters(output, now)
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

    private val windowHeader = Regex("""^Window #\d+ Window\{\S+ u\d+ (.*)\}:$""")
    private val overlayType = Regex("""(?:^|[\s{])ty=(?:TYPE_)?(?:APPLICATION_OVERLAY|SYSTEM_ALERT|SYSTEM_OVERLAY|PHONE|2038|2003|2006|2002)(?=$|[\s}])""")
    private val ownerUid = Regex("""\bmOwnerUid=(\d+)\b""")
    private val ownerPackage = Regex("""(?:^|\s)(?:ownerPackageName|package)=([a-zA-Z][\w]*(?:\.[\w]+)*)(?=$|[\s}])""")
    private val packageTitle = Regex("""([a-zA-Z][\w]*(?:\.[\w]+)+)(?:/[\w.$]+)?""")
    private val hiddenWindow = Regex("""\b(?:mHasSurface=false|mViewVisibility=(?!0x0\b)0x[0-9a-fA-F]+|isVisible=false|isOnScreen=false)\b""")
    private val windowSurface = Regex("""\bmHasSurface=true\b""")
    private val windowViewVisible = Regex("""\bmViewVisibility=0x0\b""")
    private val windowVisible = Regex("""\b(?:isVisible|isOnScreen)=true\b""")

    private fun overlayWindows(output: String, now: Long): List<WakeObservation> {
        val packages = sortedSetOf<String>()
        var window: MatchResult? = null
        var indent = 0
        var foundWindow = false
        val fields = StringBuilder()
        fun finishWindow() {
            val title = window?.groupValues?.get(1) ?: return
            if (overlayType.containsMatchIn(fields) && !hiddenWindow.containsMatchIn(fields) &&
                (windowSurface.containsMatchIn(fields) || (windowViewVisible.containsMatchIn(fields) && windowVisible.containsMatchIn(fields)))
            ) {
                // Window titles are app-provided. Prefer the package reported by WindowManager.
                val pkg = ownerPackage.find(fields)?.groupValues?.get(1) ?: packageTitle.matchEntire(title)?.groupValues?.get(1)
                if (pkg != null) {
                    val uid = ownerUid.find(fields)?.groupValues?.get(1)?.toIntOrNull()
                    packages += if (uid != null) "$pkg/$uid" else pkg
                }
            }
            window = null
            fields.clear()
        }
        for (line in output.lineSequence()) {
            val text = line.trim()
            if (text.isEmpty()) continue
            val lineIndent = line.indexOfFirst { !it.isWhitespace() }
            val header = windowHeader.matchEntire(text)
            if (header != null || lineIndent <= indent) finishWindow()
            if (header != null) {
                window = header
                indent = lineIndent
                foundWindow = true
            } else if (window != null) {
                fields.appendLine(text)
            }
        }
        finishWindow()
        return if (foundWindow) listOf(WakeObservation(WAKE_OVERLAY_WINDOWS, now, attributes = mapOf("packages" to packages.joinToString(",")))) else emptyList()
    }

    private val audioPlayer = Regex("""^(?:\(not logged\)\s*)?(?:AudioPlaybackConfiguration\s+piid:|ID:\d+\b)""")
    private val audioState = Regex("""(?:^|\s)state:(\w+)\b""")
    private val audioUid = Regex("""(?:^|\s)u/pid:(\d+)/\d+\b""")
    private val mutedAudio = Regex("""(?:^|\s)(?:muted:true\b|mutedState:(?!none\b|0\b)\w+|(?:vol|volume):0(?:\.0+)?(?=$|\s))""")

    private fun audioPlayback(output: String, now: Long): List<WakeObservation> {
        val active = sortedSetOf<Int>()
        var playersIndent: Int? = null
        for (line in output.lineSequence()) {
            val text = line.trim()
            if (playersIndent == null) {
                if (text == "players:") playersIndent = line.indexOfFirst { !it.isWhitespace() }
                continue
            }
            if (!audioPlayer.containsMatchIn(text)) {
                // A peer section header ends the player inventory.
                if (text.isEmpty() || (
                        ':' in text && line.indexOfFirst { !it.isWhitespace() } <= playersIndent &&
                            !text.startsWith("ID:") && !text.startsWith("AudioPlaybackConfiguration") &&
                            !text.startsWith("(not logged)") && "state:" !in text && "u/pid:" !in text
                        )
                ) {
                    break
                }
                return emptyList()
            }
            val state = audioState.find(text)?.groupValues?.get(1) ?: return emptyList()
            if (state != "started") continue
            val uid = audioUid.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return emptyList()
            if (isRegularAppUid(uid) && !mutedAudio.containsMatchIn(text)) active += uid
        }
        return if (playersIndent != null) listOf(WakeObservation(WAKE_AUDIO_PLAYBACK, now, attributes = mapOf("uids" to active.joinToString(",")))) else emptyList()
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

    private val widgetUser = Regex("""^provider=ProviderId\{user:(\d+),""")

    private fun grantUser(user: Int?): Map<String, String> = user?.let { mapOf("user" to it.toString()) }.orEmpty()

    private fun widgetProviders(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            if (text.startsWith("provider=")) {
                component.find(text)?.value?.let {
                    val profile = widgetUser.find(text)?.groupValues?.get(1)?.toIntOrNull()
                    add(WakeObservation("WidgetProvider", now, component = it, targetPackage = it.substringBefore('/'), attributes = grantUser(profile)))
                }
            }
        }
    }

    private val policyUser = Regex("""User (\d+):""")
    private val profileOwner = Regex("""Profile Owner \(User (\d+)\):\s*(.*)""")
    private val deviceOwner = Regex("""Device Owner:\s*(.*)""")
    private val activeAdmins = Regex("""(?:ActiveDeviceAdmins|Active (?:Device )?Admins|Enabled Device Admins)(?: \(User (\d+)[^)]*\))?:\s*(.*)""")
    private val ownerUserId = Regex("""User ID:\s*(\d+)""")

    private fun deviceAdmins(output: String, now: Long, profile: Int): List<WakeObservation> = buildList {
        var currentUser = profile
        var sectionUser = profile
        var sectionIndent = -1
        var isDeviceOwner = false
        val admins = mutableListOf<String>()

        fun addAdmin(text: String) {
            val entry = text.removePrefix("admin=").removePrefix("component=").trimStart()
            val name = if (entry.startsWith("ComponentInfo{")) {
                component.matchEntire(entry.substringAfter('{').substringBefore('}'))
            } else {
                component.matchEntire(entry.removeSuffix(":"))
            }
            name?.value?.let(admins::add)
        }

        fun recordAdmins() {
            for (admin in admins) {
                add(WakeObservation(WAKE_DEVICE_ADMIN, now, targetPackage = admin.substringBefore('/'), component = admin, attributes = mapOf("user" to sectionUser.toString())))
            }
            admins.clear()
        }

        for (line in output.lineSequence()) {
            val indent = line.indexOfFirst { !it.isWhitespace() }
            if (indent < 0) continue
            val text = line.trim()
            if (indent <= sectionIndent) {
                recordAdmins()
                sectionIndent = -1
            }
            val userHeader = policyUser.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()
            if (userHeader != null) {
                currentUser = userHeader
                if (sectionIndent >= 0 && !isDeviceOwner) {
                    recordAdmins()
                    sectionUser = userHeader
                }
                continue
            }
            val owner = deviceOwner.matchEntire(text)
            val profileHeader = profileOwner.matchEntire(text)
            val active = activeAdmins.matchEntire(text)
            val section = owner ?: profileHeader ?: active
            when {
                section != null -> {
                    recordAdmins()
                    sectionIndent = indent
                    isDeviceOwner = owner != null
                    sectionUser = if (isDeviceOwner) 0 else section.groupValues[1].toIntOrNull() ?: currentUser
                    addAdmin(section.groupValues.last())
                }

                sectionIndent >= 0 -> {
                    val ownerId = if (isDeviceOwner) ownerUserId.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull() else null
                    if (ownerId != null) sectionUser = ownerId else addAdmin(text)
                }
            }
        }
        recordAdmins()
    }

    private fun roleHolders(lines: Sequence<String>, now: Long, profile: Int): List<WakeObservation> = buildList {
        var host: String? = null
        var sourceUser = profile
        for (text in lines) {
            if (text.startsWith("user_id=")) sourceUser = text.substringAfter('=').toIntOrNull() ?: profile
            if (text.startsWith("name=android.app.role.")) host = text.substringAfter('=')
            if (text.startsWith("holders=") && host != null) {
                val holders = text.substringAfter('=').trim('[', ']').split(", ").filter { it.matches(Regex("""[\w.]+""")) }
                val user = mapOf("user" to sourceUser.toString())
                for (pkg in holders.ifEmpty { listOf(null) }) {
                    add(WakeObservation("RoleHolder", now, targetPackage = pkg, action = host, attributes = user))
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

    private val alarmSnapshotHeaders = listOf("Alarm Manager", "Pending alarm batches", "Next wake from idle", "Pending intent")

    // ICU, which backs java.util.regex on Android, rejects an unescaped closing brace.
    private val pendingAlarmHost = Regex("""(?:whenElapsed|when)\s+\d+\s+([\w.]+)\}""")

    private fun pendingAlarms(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        var host: String? = null
        var sawAlarmHeader = false
        for (text in lines) {
            if (alarmSnapshotHeaders.any { text.contains(it, ignoreCase = true) }) sawAlarmHeader = true
            if ("Alarm{" in text) host = pendingAlarmHost.find(text)?.groupValues?.get(1)
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
        if (isEmpty() && sawAlarmHeader) add(WakeObservation("PendingAlarm", now))
    }

    private val companionShortPackage = Regex("""(?:^|, )pkg=u(\d+)/([\w.]+)(?=[, }]|$)""")
    private val companionHeader = Regex("""^Association(?:Info)?\s*\{""")
    private val companionPackage = Regex("""(?:\{|,)\s*(?:mPackageName|packageName|package)\s*=\s*'?([\w.]+)""")
    private val companionUser = Regex("""(?:\{|,)\s*(?:mUserId|userId)\s*=\s*(\d+)\b""")

    private fun companionAssociations(lines: Sequence<String>, now: Long): List<WakeObservation> = buildList {
        for (text in lines) {
            val short = companionShortPackage.find(text.takeIf { it.startsWith("id=") }.orEmpty())
            val long = text.takeIf(companionHeader::containsMatchIn)
            val pkg = short?.groupValues?.get(2) ?: long?.let { companionPackage.find(it)?.groupValues?.get(1) } ?: continue
            val profile = short?.groupValues?.get(1) ?: long?.let { companionUser.find(it)?.groupValues?.get(1) }
            add(WakeObservation("CompanionAssociation", now, targetPackage = pkg, attributes = grantUser(profile?.toIntOrNull())))
        }
    }

    private val wallpaperUser = Regex("""^User (\d+):""")

    private fun wallpaperBindings(output: String, now: Long): List<WakeObservation> = buildList {
        var profile: Int? = null
        var userIndent = -1
        for (line in output.lineSequence()) {
            val indent = line.indexOfFirst { !it.isWhitespace() }
            if (indent < 0) continue
            val text = line.trim()
            // Display state shares the wallpaper user header's indentation.
            if (indent <= userIndent && text != "Display state:") profile = null
            val header = wallpaperUser.find(text)
            if (header != null) {
                profile = header.groupValues[1].toIntOrNull()
                userIndent = indent
            }
            if (text.startsWith("mWallpaperComponent=")) {
                val name = component.find(text.substringAfter('='))?.value ?: continue
                add(WakeObservation("WallpaperBinding", now, component = name, targetPackage = name.substringBefore('/'), attributes = grantUser(profile)))
            }
        }
    }

    private val syncUser = Regex("""^Sync adapters for UserInfo\{(\d+):""")
    private val syncUid = Regex("""\buid[=: ]+(\d+)$""")
    private val syncPackage = Regex("""\bpackageName[=: ]+([\w.]+)""")

    private fun syncAdapters(output: String, now: Long): List<WakeObservation> = buildList {
        var profile: Int? = null
        var userIndent = -1
        for (line in output.lineSequence()) {
            val indent = line.indexOfFirst { !it.isWhitespace() }
            if (indent < 0) continue
            val text = line.trim()
            if (indent <= userIndent) profile = null
            val header = syncUser.find(text)
            if (header != null) {
                profile = header.groupValues[1].toIntOrNull()
                userIndent = indent
            }
            if (text.startsWith("ServiceInfo: SyncAdapterType")) {
                val pkg = syncPackage.find(text)?.groupValues?.get(1) ?: component.find(text.substringAfter('}'))?.value?.substringBefore('/')
                    ?: continue
                val uid = syncUid.find(text)?.groupValues?.get(1)?.toIntOrNull()
                add(WakeObservation("SyncAdapter", now, targetPackage = pkg, attributes = grantUser(uid?.let(::userOfUid) ?: profile)))
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
