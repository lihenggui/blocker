// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.Intent
import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import java.util.concurrent.TimeUnit

// Display screen-off durations shorter than a minute as one minute.
private const val MINIMUM_DISPLAY_MINUTES = 1L

// Preserve the final six digits for Android's Int-based plural selection on large counts.
private const val PLURAL_QUANTITY_MODULUS = 1_000_000

@Composable
internal fun WakeHeadline.reasonText(): String = reasonText(LocalResources.current)

internal fun WakeHeadline.reasonText(resources: Resources): String = with(resources) {
    when (val headline = this@reasonText) {
        is WakeHeadline.RestartedAfterClose -> countText(plurals.feature_wake_api_reason_restarted, headline.times)

        is WakeHeadline.StartedOthers -> countText(plurals.feature_wake_api_reason_started_others, headline.apps.toLong())

        is WakeHeadline.StartedByOthers -> getQuantityString(
            plurals.feature_wake_api_reason_started_by_others,
            headline.apps,
            headline.apps,
            countText(plurals.feature_wake_api_times, headline.times),
        )

        is WakeHeadline.RanWithScreenOff -> getString(string.feature_wake_api_reason_screen_off, wakeDuration(headline.nanos))

        is WakeHeadline.StartsOnSystemEvent -> if (headline.action.isWakeBootAction()) {
            getString(string.feature_wake_api_reason_boot)
        } else {
            countText(plurals.feature_wake_api_reason_system_broadcast, headline.times)
        }

        is WakeHeadline.TimedWakeups -> countText(plurals.feature_wake_api_reason_timed, headline.times)

        is WakeHeadline.BackgroundStarts -> countText(plurals.feature_wake_api_reason_background, headline.times)
    }
}

internal fun Resources.countText(@PluralsRes id: Int, count: Long): String {
    val quantity = if (count > Int.MAX_VALUE) {
        PLURAL_QUANTITY_MODULUS + (count % PLURAL_QUANTITY_MODULUS).toInt()
    } else {
        count.toInt()
    }
    return getQuantityString(id, quantity, count)
}

internal fun Resources.wakeDuration(nanos: Long): String {
    val minutes = TimeUnit.NANOSECONDS.toMinutes(nanos).coerceAtLeast(MINIMUM_DISPLAY_MINUTES)
    val hours = TimeUnit.MINUTES.toHours(minutes)
    val remainingMinutes = minutes - TimeUnit.HOURS.toMinutes(hours)
    val formatter = MeasureFormat.getInstance(configuration.locales[0], MeasureFormat.FormatWidth.SHORT)
    return when {
        hours == 0L -> formatter.formatMeasures(Measure(minutes, MeasureUnit.MINUTE))
        remainingMinutes == 0L -> formatter.formatMeasures(Measure(hours, MeasureUnit.HOUR))
        else -> formatter.formatMeasures(Measure(hours, MeasureUnit.HOUR), Measure(remainingMinutes, MeasureUnit.MINUTE))
    }
}

internal fun String?.isWakeBootAction(): Boolean = this == Intent.ACTION_BOOT_COMPLETED || this == Intent.ACTION_LOCKED_BOOT_COMPLETED
