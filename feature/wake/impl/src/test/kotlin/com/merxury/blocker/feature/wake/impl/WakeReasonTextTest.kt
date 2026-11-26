// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.wake.WakeHeadline
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class WakeReasonTextTest {

    @Test
    fun givenEachCountHeadline_whenFormatting_thenUsesItsOwnReasonAndQuantity() {
        val english = resources(Locale.US)
        val reasons = mapOf(
            WakeHeadline.RestartedAfterClose(1) to "Restarted 1 time after being closed",
            WakeHeadline.RestartedAfterClose(4) to "Restarted 4 times after being closed",
            WakeHeadline.StartedOthers(1) to "Started 1 other app",
            WakeHeadline.StartedOthers(2) to "Started 2 other apps",
            WakeHeadline.StartedByOthers(1, 2) to "Started 2 times by 1 app",
            WakeHeadline.StartedByOthers(3, 1) to "Started 1 time by 3 apps",
            WakeHeadline.StartsOnSystemEvent(Intent.ACTION_SCREEN_ON, 1) to "Started 1 time by a system broadcast",
            WakeHeadline.StartsOnSystemEvent(Intent.ACTION_SCREEN_ON, 2) to "Started 2 times by system broadcasts",
            WakeHeadline.TimedWakeups(2) to "Woke 2 times on a timer",
            WakeHeadline.BackgroundStarts(2) to "Started 2 times in the background",
        )

        reasons.forEach { (headline, text) -> assertThat(headline.reasonText(english)).isEqualTo(text) }
    }

    @Test
    fun givenBothBootActions_whenFormatting_thenUsesTheBootReason() {
        val english = resources(Locale.US)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            assertThat(WakeHeadline.StartsOnSystemEvent(action, 4).reasonText(english)).isEqualTo("Starts at boot")
        }
    }

    @Test
    fun givenScreenOffDurations_whenFormatting_thenRoundsDownWithAtLeastOneMinute() {
        val english = resources(Locale.US)
        val cases = mapOf(
            0L to "1 min",
            59L to "1 min",
            3599L to "59 min",
            3600L to "1 hr",
            7319L to "2 hr 1 min",
        )
        cases.forEach { (seconds, duration) ->
            val headline = WakeHeadline.RanWithScreenOff(TimeUnit.SECONDS.toNanos(seconds))
            assertThat(headline.reasonText(english)).isEqualTo("Ran for $duration with the screen off")
        }
    }

    @Test
    fun givenChineseScreenOffDurations_whenFormatting_thenSeparatesNumbersAndUnitsWithSpaces() {
        val chinese = resources(Locale.SIMPLIFIED_CHINESE)
        assertThat(WakeHeadline.RanWithScreenOff(TimeUnit.MINUTES.toNanos(52)).reasonText(chinese))
            .isEqualTo("熄屏后运行 52 分钟")
        assertThat(WakeHeadline.RanWithScreenOff(TimeUnit.MINUTES.toNanos(120)).reasonText(chinese))
            .isEqualTo("熄屏后运行 2 小时")
        assertThat(WakeHeadline.RanWithScreenOff(TimeUnit.MINUTES.toNanos(130)).reasonText(chinese))
            .isEqualTo("熄屏后运行 2 小时 10 分钟")
    }

    @Test
    fun givenRussianCounts_whenFormatting_thenUsesTheLocaleQuantityWithoutTruncatingLargeCounts() {
        val russian = resources(Locale.forLanguageTag("ru"))
        assertThat(WakeHeadline.RestartedAfterClose(1).reasonText(russian)).isEqualTo("Перезапустилось 1 раз после закрытия")
        assertThat(WakeHeadline.RestartedAfterClose(22).reasonText(russian)).isEqualTo("Перезапустилось 22 раза после закрытия")
        assertThat(WakeHeadline.RestartedAfterClose(25).reasonText(russian)).isEqualTo("Перезапустилось 25 раз после закрытия")
        assertThat(WakeHeadline.RestartedAfterClose(2_147_483_652L).reasonText(russian))
            .isEqualTo("Перезапустилось 2147483652 раза после закрытия")
    }

    private fun resources(locale: Locale): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources
    }
}
