package com.merxury.blocker.core.designsystem.segmentedbuttons

import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.merxury.blocker.core.designsystem.R
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

/**
 * items : list of items to be render
 * defaultSelectedItemIndex : to highlight item by default (Optional)
 * useFixedWidth : set true if you want to set fix width to item (Optional)
 * itemWidth : Provide item width if useFixedWidth is set to true (Optional)
 * cornerRadius : To make control as rounded (Optional)
 * color : Set color to control (Optional)
 * onItemSelection : Get selected item index
 */
@Composable
fun <T> SegmentedButtons(
    items: List<Pair<T, Int>>,
    selectedValue: T,
    cornerRadius: Int = 50,
    @ColorRes color: Color = MaterialTheme.colorScheme.primary,
    onItemSelection: (item: T) -> Unit,
) {
    val selectedItem = remember { mutableStateOf(selectedValue) }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        items.forEachIndexed { index, item ->
            OutlinedButton(
                modifier = when (index) {
                    0 -> {
                        Modifier
                            .wrapContentSize()
                            .offset(0.dp, 0.dp)
                            .zIndex(if (selectedItem.value == item.first) 1f else 0f)
                    }

                    else -> {
                        Modifier
                            .wrapContentSize()
                            .offset((-1 * index).dp, 0.dp)
                            .zIndex(if (selectedItem.value == item.first) 1f else 0f)
                    }
                },
                onClick = {
                    selectedItem.value = item.first
                    onItemSelection(item.first)
                },
                shape = when (index) {
                    0 -> RoundedCornerShape(
                        topStartPercent = cornerRadius,
                        topEndPercent = 0,
                        bottomStartPercent = cornerRadius,
                        bottomEndPercent = 0,
                    )

                    items.size - 1 -> RoundedCornerShape(
                        topStartPercent = 0,
                        topEndPercent = cornerRadius,
                        bottomStartPercent = 0,
                        bottomEndPercent = cornerRadius,
                    )

                    else -> RoundedCornerShape(
                        topStartPercent = 0,
                        topEndPercent = 0,
                        bottomStartPercent = 0,
                        bottomEndPercent = 0,
                    )
                },
                border = BorderStroke(
                    1.dp,
                    if (selectedItem.value == item.first) {
                        color
                    } else {
                        color.copy(alpha = 0.75f)
                    },
                ),
                colors = if (selectedItem.value == item.first) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = color,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                },
            ) {
                Text(
                    text = stringResource(id = item.second),
                    color = if (selectedItem.value == item.first) {
                        Color.White
                    } else {
                        color.copy(alpha = 0.9f)
                    },
                )
            }
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light theme")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark theme")
fun SegmentedButtonsPreview() {
    val list = listOf(0 to R.string.back, 1 to R.string.back)
    BlockerTheme {
        SegmentedButtons(
            items = list,
            selectedValue = 1,
            onItemSelection = { },
        )
    }
}
