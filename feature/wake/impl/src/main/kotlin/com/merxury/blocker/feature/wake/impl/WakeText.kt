// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.res.Resources
import android.text.format.DateUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

internal sealed interface WakeText {
    data class Resource(@StringRes val id: Int, val args: List<Any>) : WakeText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }
    data class Count(@PluralsRes val id: Int, val amount: Long) : WakeText
    data class Latest(val count: Long, val millis: Long) : WakeText
    data class Duration(val nanos: Long) : WakeText
    data class Literal(val value: String) : WakeText
    data class Callers(val labels: List<String>) : WakeText
}

@Composable
internal fun WakeText.resolve(): String = resolve(LocalResources.current)

internal fun WakeText.resolve(resources: Resources): String = with(resources) {
    when (val text = this@resolve) {
        is WakeText.Resource -> getString(text.id, *text.args.map { (it as? WakeText)?.resolve(resources) ?: it }.toTypedArray())

        is WakeText.Count -> countText(text.id, text.amount)

        is WakeText.Latest -> getString(
            string.feature_wake_api_path_meta,
            countText(plurals.feature_wake_api_times, text.count),
            DateUtils.getRelativeTimeSpanString(text.millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )

        is WakeText.Duration -> wakeDuration(text.nanos)

        is WakeText.Literal -> text.value

        is WakeText.Callers -> text.labels.joinToString("、")
    }
}
