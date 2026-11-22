// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class WakeProcStatsTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/wake/$name")).readText()
    private val gsa = "com.google.android.googlequicksearchbox"

    @Test fun givenCurrentCheckin_whenParsing_thenOnlyAppHoldStatesAndActiveAssociationsRemain() {
        // Up to Android 14 each process's own screen buckets are kept.
        val period = WakeProcStats.parse(fixture("procstats-current.txt"), 34, utc)

        assertThat(period.id).isEqualTo("2026-11-22-08-13-53")
        assertThat(period.startWallMillis).isEqualTo(1_795_335_233_000L)
        assertThat(period.endWallMillis - period.startWallMillis).isEqualTo(1_562_445L - 10_784L)
        // Bound-top (z), last-activity (l), cached (a) and frozen (e) are excluded. The system uid 1073 is not an app.
        // The foreground service state "g" is also kept on its own.
        assertThat(period.holds).containsExactly(
            WakeProcStatsHold(gsa, 10144, "$gsa:search", 0, 230_579),
            WakeProcStatsHold(gsa, 10144, "$gsa:interactor", 0, 716_484),
            WakeProcStatsHold("com.google.android.apps.messaging", 10136, "com.google.android.apps.messaging", 0, 685_643, 0, 265),
            WakeProcStatsHold("com.google.android.apps.wellbeing", 10142, "com.google.android.apps.wellbeing", 0, 120_477, 0, 75),
            WakeProcStatsHold("com.google.android.apps.wellbeing", 1110142, "com.google.android.apps.wellbeing", 0, 1_818, 0, 56),
        )
        // Bound and active times are both kept; the second system binding was never active. The app's
        // own processes binding each other are kept too.
        assertThat(period.associations.map { listOf(it.component.substringAfterLast('.'), it.sourceUid, it.boundMillis, it.activeMillis) }).containsExactly(
            listOf("GsaPublicContentProvider", 10179, 123_719L, 16_921L),
            listOf("SearchService", 10144, 6_860L, 1_088L),
            listOf("MultiInstanceInvalidationService", 10144, 60_022L, 4_112L),
            listOf("PublicSearchService", 10144, 1_613L, 89L),
            listOf("SearchProcessEndpointService", 10144, 69_325L, 4_380L),
            listOf("PodcastBrowserService", 1002, 672L, 137L),
            listOf("OneSearchSuggestService", 10179, 237_259L, 15_212L),
            listOf("GsaVoiceInteractionService", 1000, 1_544_307L, 50_782L),
            listOf("ScheduledTaskService", 1000, 1_935L, 0L),
            listOf("BackgroundTasksJobService", 1000, 5_534L, 23L),
        )
    }

    @Test fun givenAndroid15Checkin_whenParsing_thenHeldTimeIsSplitByThePeriodsScreenShare() {
        // Captured on the API 35 emulator after 626 s of pretended screen-off. The ":rcs" process was
        // held all period, yet its own bucket booked no screen-off time; the main process kept booking
        // screen-off after the screen was back on.
        val messaging = "com.google.android.apps.messaging"
        fun holds(sdk: Int) = WakeProcStats.parse(fixture("procstats-api35.txt"), sdk, utc).holds.associateBy { it.process }
        val own = holds(34)
        assertThat(own.getValue(messaging)).isEqualTo(WakeProcStatsHold(messaging, 10136, messaging, 133_940, 738_385, 0, 204))
        assertThat(own.getValue("$messaging:rcs")).isEqualTo(WakeProcStatsHold(messaging, 10136, "$messaging:rcs", 0, 2_975_760))
        // total: 625 972 ms of 2 983 455 ms were screen-off.
        val shared = holds(35)
        assertThat(shared.getValue(messaging)).isEqualTo(WakeProcStatsHold(messaging, 10136, messaging, 183_026, 689_299, 42, 162))
        assertThat(shared.getValue("$messaging:rcs")).isEqualTo(WakeProcStatsHold(messaging, 10136, "$messaging:rcs", 624_357, 2_351_403))
        val api35 = fixture("procstats-api35.txt")
        val missing = assertThrows(IllegalArgumentException::class.java) { WakeProcStats.parse(api35.lines().filterNot { it.startsWith("total,") }.joinToString("\n"), 35, utc) }
        assertThat(missing.message).contains("no total line")
        assertThrows(IllegalArgumentException::class.java) { WakeProcStats.parse(api35.replace("total,0n:", "total,0x:"), 35, utc) }
        // Before Android 15 the total line is not needed.
        assertThat(WakeProcStats.parse(api35.lines().filterNot { it.startsWith("total,") }.joinToString("\n"), 34, utc).holds).hasSize(5)
    }

    @Test fun givenSharedProcess_whenParsing_thenItsTimeIsSharedEvenlyBetweenItsPackages() {
        // android.process.acore hosts three providers; each package lists the whole process time.
        val acore = WakeProcStats.parse(fixture("procstats-api35.txt"), 34, utc).holds.filter { it.process == "android.process.acore" }
        assertThat(acore.map { it.target to it.screenOnMillis }).containsExactly(
            "com.android.providers.blockednumber" to 43_294L,
            "com.android.providers.contacts" to 43_293L,
            "com.android.providers.userdictionary" to 43_293L,
        )
        // Their sum is the process's own held time (the proc line), not three times it.
        assertThat(acore.sumOf { it.screenOnMillis + it.screenOffMillis }).isEqualTo(129_880)
        val split = WakeProcStats.parse(fixture("procstats-api35.txt"), 35, utc).holds.filter { it.process == "android.process.acore" }
        assertThat(split.sumOf { it.screenOnMillis + it.screenOffMillis }).isEqualTo(129_880)
        // Without a proc line the largest package copy stands for the process.
        val output = """
            vers,5
            period,2026-01-01-00-00-00,0,1000,partial
            pkgproc,example.one,10123,1,shared.process,0ns:10,1ns:5
            pkgproc,example.two,10123,1,shared.process,0ns:9,1ns:5
        """.trimIndent()
        assertThat(WakeProcStats.parse(output, 34, utc).holds.map { Triple(it.target, it.screenOffMillis, it.screenOnMillis) }).containsExactly(
            Triple("example.one", 5L, 3L),
            Triple("example.two", 5L, 2L),
        )
    }

    @Test fun givenOwnProcessesBindingEachOther_whenParsing_thenTheAssociationIsKept() {
        val associations = WakeProcStats.parse(fixture("procstats-api35.txt"), 35, utc).associations
            .filter { it.sourceUid == it.uid }.associateBy { it.component.substringAfterLast('.') }
        // The ":rcs" process was a bound foreground service all period, but procstats reports it active for about half.
        assertThat(associations.getValue("SharedStorageProvider").sourceProcess).isEqualTo("com.google.android.apps.messaging:rcs")
        assertThat(associations.getValue("SharedStorageProvider").boundMillis).isEqualTo(882_241)
        assertThat(associations.getValue("SharedStorageProvider").activeMillis).isEqualTo(482_901)
        assertThat(associations.getValue("JibeService").sourceProcess).isEqualTo("com.google.android.apps.messaging")
    }

    @Test fun givenScreenOffStates_whenParsing_thenScreenStatesStaySeparate() {
        val output = """
            vers,5
            period,2026-01-01-00-00-00,0,1000,partial
            pkgproc,example.app,10123,1,,0ns:100,0mg:20,1ns:7,0na:500,0nx:900,1nt:4
            pkgproc,example.app,10123,1,.remote,0nr:3
            pkgproc,example.app,10123,1,example.app:push,0nb:11
        """.trimIndent()
        val holds = WakeProcStats.parse(output, 34, utc).holds.associateBy { it.process }
        assertThat(holds.getValue("example.app").screenOffMillis).isEqualTo(120)
        assertThat(holds.getValue("example.app").fgsScreenOffMillis).isEqualTo(20)
        assertThat(holds.getValue("example.app").screenOnMillis).isEqualTo(7)
        assertThat(holds.getValue("example.app.remote").screenOffMillis).isEqualTo(3)
        assertThat(holds.getValue("example.app:push").screenOffMillis).isEqualTo(11)
    }

    @Test fun givenNonRegularAppUids_whenParsing_thenOnlyRowsOfRegularAppUidsRemain() {
        // procstats files isolated renderers under the host uid, as seen on the emulator. SDK sandbox
        // and isolated uids are not the host's own rows.
        val output = """
            vers,5
            period,2026-01-01-00-00-00,0,1000,partial
            pkgproc,com.android.chrome,10145,1,com.android.chrome:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0,0ns:9,0na:40
            pkgproc,com.google.android.sdksandbox,1020145,1,com.google.android.sdksandbox,0ns:50
            pkgproc,com.android.chrome,99012,1,com.android.chrome:sandboxed_process1,0ns:70
            pkgasc,com.google.android.sdksandbox,1020145,1,.Service,system,1000,1,5,1,s:5
        """.trimIndent()
        val period = WakeProcStats.parse(output, 34, utc)
        assertThat(period.holds.map { it.uid to it.screenOffMillis }).containsExactly(10145 to 9L)
        assertThat(period.associations).isEmpty()
    }

    @Test fun givenReleaseSpecificLetters_whenParsing_thenEachReleaseUsesItsOwnTable() {
        fun parse(sdk: Int, tag: String) = WakeProcStats.parse("vers,5\nperiod,2026-01-01-00-00-00,0,1,partial\npkgproc,a.b,10001,1,,$tag:5\ntotal,0n:1", sdk, utc).holds
        // Android 11 reports foreground services as important foreground; "c" is a cached state there.
        assertThat(parse(30, "0nf").single().screenOffMillis).isEqualTo(5)
        assertThat(parse(30, "0nf").single().fgsScreenOffMillis).isEqualTo(0)
        assertThat(parse(33, "0ng").single().fgsScreenOffMillis).isEqualTo(5)
        assertThat(parse(30, "0nc")).isEmpty()
        // Android 12 and 13 merge bound-top with bound foreground service in "d".
        assertThat(parse(33, "0ng").single().screenOffMillis).isEqualTo(5)
        assertThat(parse(33, "0nd")).isEmpty()
        // Android 14 and later: "y" is bound foreground service, "e" is frozen, and "c" no longer exists.
        assertThat(parse(35, "0ny").single().screenOffMillis).isEqualTo(5)
        assertThat(parse(35, "0ne")).isEmpty()
        assertThrows(IllegalArgumentException::class.java) { parse(35, "0nc") }
        assertThrows(IllegalArgumentException::class.java) { parse(30, "0ny") }
        assertThrows(IllegalArgumentException::class.java) { parse(38, "0ns") }
    }

    @Test fun givenUnknownCheckinVersion_whenParsing_thenItIsRefused() {
        val output = fixture("procstats-current.txt").replace("vers,5", "vers,6")
        val error = assertThrows(IllegalArgumentException::class.java) { WakeProcStats.parse(output, 35, utc) }
        assertThat(error.message).contains("unsupported checkin version 6")
        assertThrows(IllegalArgumentException::class.java) { WakeProcStats.parse("err,Failure reading state-x.bin", 35, utc) }
    }

    @Test fun givenCommittedPeriods_whenCurrentPeriodRolledOver_thenNewestUpToTheLastSeenPeriodAreRead() {
        val committed = WakeProcStats.parse(fixture("procstats-last.txt"), 34, utc)
        assertThat(committed.id).isEqualTo("2026-11-20-22-26-51")
        assertThat(WakeProcStats.committedCount("LAST STATS AT INDEX 1000:\nOnly have 5 data sets")).isEqualTo(5)

        fun period(id: String) = committed.copy(id = id)
        val files = listOf("2026-11-20-19", "2026-11-20-22", "2026-11-21-08", "2026-11-21-11", "2026-11-21-14").map(::period)
        val read = mutableListOf<Int>()
        // The previous current period committed and is now the newest file, at index count - 1.
        assertThat(
            WakeProcStats.committed("2026-11-21-14", 5) {
                read += it
                files[it]
            }.map { it.id },
        ).containsExactly("2026-11-21-14")
        assertThat(read).containsExactly(4)
        // Two rollovers since the last run: both final periods are returned.
        assertThat(WakeProcStats.committed("2026-11-21-11", 5) { files[it] }.map { it.id }).containsExactly("2026-11-21-14", "2026-11-21-11")
        // The last seen period was pruned: stop at the first older file and report nothing for it.
        assertThat(WakeProcStats.committed("2026-11-21-12", 5) { files[it] }.map { it.id }).containsExactly("2026-11-21-14")
        // Index 0 cannot be requested.
        assertThat(WakeProcStats.committed("2026-11-20-19", 1) { files[it] }).isEmpty()
    }
}
