// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.Intent
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import java.util.concurrent.TimeUnit

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

        is WakeHeadline.StartsOnSystemEvent -> when (headline.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> getString(string.feature_wake_api_reason_boot)
            else -> countText(plurals.feature_wake_api_reason_system_broadcast, headline.times)
        }

        is WakeHeadline.TimedWakeups -> countText(plurals.feature_wake_api_reason_timed, headline.times)

        is WakeHeadline.BackgroundStarts -> countText(plurals.feature_wake_api_reason_background, headline.times)
    }
}

private fun Resources.countText(@PluralsRes id: Int, count: Long): String {
    // Android selects plurals with an Int. Preserve the final six digits for the supported locales
    // while keeping large counts distinct from the small quantities with their own forms.
    val quantity = if (count > Int.MAX_VALUE) 1_000_000 + (count % 1_000_000).toInt() else count.toInt()
    return getQuantityString(id, quantity, count)
}

private fun Resources.wakeDuration(nanos: Long): String {
    val minutes = TimeUnit.NANOSECONDS.toMinutes(nanos).coerceAtLeast(1)
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0L -> countText(plurals.feature_wake_api_duration_minutes, minutes)

        remainingMinutes == 0L -> countText(plurals.feature_wake_api_duration_hours, hours)

        else -> getString(
            string.feature_wake_api_duration_hours_minutes,
            countText(plurals.feature_wake_api_duration_hours, hours),
            countText(plurals.feature_wake_api_duration_minutes, remainingMinutes),
        )
    }
}
