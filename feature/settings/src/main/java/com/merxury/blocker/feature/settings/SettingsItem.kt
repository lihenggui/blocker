package com.merxury.blocker.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R.string

@Composable
fun ControllerItem() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = string.key_pref_controller_settings),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
        )
        Row(modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)) {
            Icon(
                modifier = Modifier.padding(4.dp),
                imageVector = BlockerIcons.AutoFix,
                contentDescription = null
            )
            Text(
                text = stringResource(id = string.key_pref_controller_type),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun PreviewControllerItem() {
    BlockerTheme {
        ControllerItem()
    }
}
