/*
 * Copyright 2023 Blocker
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

class EnterAlwaysCollapsedState(
    heightRange: IntRange,
    scrollOffset: Float = 0f,
) : ScrollFlagState(heightRange) {

    override var internalScrollOffset by mutableFloatStateOf(
        value = scrollOffset.coerceIn(0f, maxHeight.toFloat()),
    )

    override val offset: Float
        get() = if (scrollOffset > rangeDifference) {
            -(scrollOffset - rangeDifference).coerceIn(0f, minHeight.toFloat())
        } else {
            0f
        }

    override var scrollOffset: Float
        get() = internalScrollOffset
        set(value) {
            val oldOffset = internalScrollOffset
            internalScrollOffset = if (scrollTopLimitReached) {
                value.coerceIn(0f, maxHeight.toFloat())
            } else {
                value.coerceIn(rangeDifference.toFloat(), maxHeight.toFloat())
            }
            internalConsumed = oldOffset - internalScrollOffset
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
                    EnterAlwaysCollapsedState(
                        heightRange = (it[minHeightKey] as Int)..(it[maxHeightKey] as Int),
                        scrollOffset = it[scrollOffsetKey] as Float,
                    )
                },
            )
        }
    }
}
