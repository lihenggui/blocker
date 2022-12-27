package com.merxury.blocker.feature.appdetail.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import kotlinx.datetime.Clock.System

@Composable
fun AppBasicInfoCard(
    app: Application,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    val versionName = app.versionName
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = app.label, style = MaterialTheme.typography.headlineMedium)
            Text(text = app.packageName, style = MaterialTheme.typography.bodyMedium)
            if (versionName != null) {
                Text(text = versionName, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Image(
            modifier = iconModifier
                .size(80.dp)
                .padding(vertical = 40.dp),
            painter = rememberAsyncImagePainter(
                LocalContext.current.packageManager.getApplicationIcon(app.packageName)
            ),
            contentDescription = null
        )
    }
}

@Composable
@Preview
fun PreviewAppBasicInfoCard() {
    val app = Application(
        label = "Blocker",
        packageName = "com.mercury.blocker",
        versionName = "1.2.69-alpha",
        isEnabled = false,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
        packageInfo = null,
    )
    BlockerTheme {
        Surface {
            AppBasicInfoCard(
                app = app
            )
        }
    }
}
