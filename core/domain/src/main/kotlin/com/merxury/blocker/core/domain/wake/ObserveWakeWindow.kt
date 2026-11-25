// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** Keeps active observations on the current local-day window even without a database write at midnight. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> observeWakeWindow(days: Int, observe: (fromDay: Long, throughDay: Long) -> Flow<T>): Flow<T> {
    require(days > 0) { "A wake activity window must contain at least one day" }
    return flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val today = now.toLocalDate()
            emit(today.toEpochDay())
            val nextDay = today.plusDays(1).atStartOfDay(zone)
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1))
        }
    }.flatMapLatest { throughDay -> observe(throughDay - days + 1, throughDay) }
}
