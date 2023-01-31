package com.merxury.blocker.core.ui.state.toolbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

class EnterAlwaysState(
    heightRange: IntRange,
    scrollValue: Int = 0,
    scrollOffset: Float = 0f,
) : DynamicOffsetScrollFlagState(heightRange, scrollValue) {

    override var scrollOffset by mutableStateOf(
        value = scrollOffset.coerceIn(0f, maxHeight.toFloat()),
        policy = structuralEqualityPolicy(),
    )

    override val offset: Float
        get() = -(scrollOffset - rangeDifference).coerceIn(0f, minHeight.toFloat())

    override val height: Float
        get() = (maxHeight - scrollOffset).coerceIn(minHeight.toFloat(), maxHeight.toFloat())

    override var scrollValue: Int
        get() = _scrollValue
        set(value) {
            val delta = (_scrollValue - value).toFloat()
            scrollOffset = (scrollOffset - delta).coerceIn(0f, maxHeight.toFloat())
            _scrollValue = value.coerceAtLeast(0)
        }

    companion object {
        val Saver = run {

            val minHeightKey = "MinHeight"
            val maxHeightKey = "MaxHeight"
            val scrollValueKey = "ScrollValue"
            val scrollOffsetKey = "ScrollOffset"

            mapSaver(
                save = {
                    mapOf(
                        minHeightKey to it.minHeight,
                        maxHeightKey to it.maxHeight,
                        scrollValueKey to it.scrollValue,
                        scrollOffsetKey to it.scrollOffset,
                    )
                },
                restore = {
                    EnterAlwaysState(
                        heightRange = (it[minHeightKey] as Int)..(it[maxHeightKey] as Int),
                        scrollValue = it[scrollValueKey] as Int,
                        scrollOffset = it[scrollOffsetKey] as Float,
                    )
                },
            )
        }
    }
}