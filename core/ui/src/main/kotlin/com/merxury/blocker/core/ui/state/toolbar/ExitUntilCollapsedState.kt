/*
 * Copyright 2025 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.ui.state.toolbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue

class ExitUntilCollapsedState(
    heightRange: IntRange,
    scrollOffset: Float = 0f,
) : FixedScrollFlagState(heightRange) {

    override var internalScrollOffset by mutableFloatStateOf(
        value = scrollOffset.coerceIn(0f, rangeDifference.toFloat()),
    )

    override var scrollOffset: Float
        get() = internalScrollOffset
        set(value) {
            if (scrollTopLimitReached) {
                val oldOffset = internalScrollOffset
                internalScrollOffset = value.coerceIn(0f, rangeDifference.toFloat())
                internalConsumed = oldOffset - internalScrollOffset
            } else {
                internalConsumed = 0f
            }
        }

    companion object {
        val Saver = run {

            val minHeightKey = "MinHeight"
            val maxHeightKey = "MaxHeight"
            val scrollOffsetKey = "ScrollOffset"

            mapSaver(
                save = {
                    mapOf(
                        minHeightKey to it.minHeight,
                        maxHeightKey to it.maxHeight,
                        scrollOffsetKey to it.scrollOffset,
                    )
                },
                restore = {
                    ExitUntilCollapsedState(
                        heightRange = (it[minHeightKey] as Int)..(it[maxHeightKey] as Int),
                        scrollOffset = it[scrollOffsetKey] as Float,
                    )
                },
            )
        }
    }
}
