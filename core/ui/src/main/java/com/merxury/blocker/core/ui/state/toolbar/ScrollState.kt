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

import androidx.compose.runtime.saveable.mapSaver

class ScrollState(
    heightRange: IntRange,
    scrollValue: Int = 0,
) : ScrollFlagState(heightRange, scrollValue) {

    override val offset: Float
        get() = -(scrollValue - rangeDifference.toFloat()).coerceIn(0f, minHeight.toFloat())

    override val height: Float
        get() = (maxHeight.toFloat() - scrollValue).coerceIn(
            minHeight.toFloat(),
            maxHeight.toFloat(),
        )

    override var scrollValue: Int
        get() = _scrollValue
        set(value) {
            _scrollValue = value.coerceAtLeast(0)
        }

    companion object {
        val Saver = run {

            val minHeightKey = "MinHeight"
            val maxHeightKey = "MaxHeight"
            val scrollValueKey = "ScrollValue"

            mapSaver(
                save = {
                    mapOf(
                        minHeightKey to it.minHeight,
                        maxHeightKey to it.maxHeight,
                        scrollValueKey to it.scrollValue,
                    )
                },
                restore = {
                    ScrollState(
                        heightRange = (it[minHeightKey] as Int)..(it[maxHeightKey] as Int),
                        scrollValue = it[scrollValueKey] as Int,
                    )
                },
            )
        }
    }
}