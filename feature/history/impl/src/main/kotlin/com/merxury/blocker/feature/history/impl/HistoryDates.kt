// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Composable
internal fun rememberHistoryDates(zone: ZoneId, today: LocalDate): HistoryDates {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val use24HourTime = DateFormat.is24HourFormat(context)
    return remember(context, locale, use24HourTime, zone, today) {
        val time = DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone(zone.id) }
        HistoryDates(zone, today, locale, time)
    }
}

internal class HistoryDates(
    private val zone: ZoneId,
    private val today: LocalDate,
    locale: Locale,
    private val timeFormat: java.text.DateFormat,
) {
    private val relative = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
    )
    private val monthDay = dateFormat(locale, "MMMd")
    private val yearMonthDay = dateFormat(locale, "yMMMd")

    fun day(date: LocalDate): String = when (date) {
        today -> relative.format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.DAY)

        today.minusDays(1) -> relative.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)

        else -> {
            val formatter = if (date.year == today.year) monthDay else yearMonthDay
            formatter.format(Date.from(date.atStartOfDay(zone).toInstant()))
        }
    }

    fun time(timestamp: Instant): String = timeFormat.format(Date(timestamp.toEpochMilliseconds()))

    @Composable
    fun dateTime(timestamp: Instant): String = stringResource(
        R.string.feature_history_impl_day_time,
        day(timestamp.toJavaInstant().atZone(zone).toLocalDate()),
        time(timestamp),
    )

    private fun dateFormat(locale: Locale, skeleton: String) = android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale).apply {
        timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
    }
}
