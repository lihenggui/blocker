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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

class EnterAlwaysState(
    heightRange: IntRange,
    scrollOffset: Float = 0f,
) : ScrollFlagState(heightRange) {

    override var _scrollOffset by mutableStateOf(
        value = scrollOffset.coerceIn(0f, maxHeight.toFloat()),
        policy = structuralEqualityPolicy(),
    )

    override val offset: Float
        get() = -(scrollOffset - rangeDifference).coerceIn(0f, minHeight.toFloat())

    override var scrollOffset: Float
        get() = _scrollOffset
        set(value) {
            val oldOffset = _scrollOffset
            _scrollOffset = value.coerceIn(0f, maxHeight.toFloat())
            _consumed = oldOffset - _scrollOffset
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
                    EnterAlwaysState(
                        heightRange = (it[minHeightKey] as Int)..(it[maxHeightKey] as Int),
                        scrollOffset = it[scrollOffsetKey] as Float,
                    )
                },
            )
        }
    }
}
