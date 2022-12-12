package com.merxury.blocker.feature.appdetail.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdetail.ComponentInfo

@Composable
fun ComponentTabContent(
    componentList: SnapshotStateList<ComponentInfo>,
    onSwitch: (ComponentInfo) -> Unit
) {
    val listContent = remember { componentList }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState
    ) {
        items(listContent) {
            AppInfoItem(
                itemName = it.componentName,
                itemDetail = it.componentDetail,
                itemValue = it.value,
                onClick = onSwitch
            )
        }
        item {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }
}

@Composable
fun AppInfoItem(
    itemName: String,
    itemDetail: String,
    itemValue: Boolean,
    onClick: (ComponentInfo) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Text(text = itemName, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = itemDetail, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.weight(1f))
        Switch(checked = itemValue, onCheckedChange = {
            onClick(
                ComponentInfo(itemName, itemDetail, itemValue)
            )
        }
        )
    }
}

@Composable
@Preview
fun PreviewAppInfoItem() {
    BlockerTheme {
        AppInfoItem(
            itemName = "AccountAuthActivity",
            itemDetail = "com.glip.foundation.settings.thirdaccount.auth.AccountAuthActivity",
            itemValue = false
        ) { }
    }
}
