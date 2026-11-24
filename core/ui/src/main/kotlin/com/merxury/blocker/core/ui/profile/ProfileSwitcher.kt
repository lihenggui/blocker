/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtonItem
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.ui.R.string

/** A user in the profile switcher. */
data class ProfileTab(
    val user: UserId,
    val label: ProfileLabel,
    val running: Boolean,
)

fun DeviceUser.toProfileTab() = ProfileTab(
    user = id,
    label = profileLabel(),
    running = running,
)

/**
 * Switches a screen between the users in [profiles], whose apps it shows one user at a time; a
 * stopped user says it is not running.
 */
@Composable
fun ProfileSwitcher(
    profiles: List<ProfileTab>,
    selectedUser: UserId,
    onSelect: (UserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedButtons(modifier = modifier.padding(vertical = 8.dp).testTag("profileSwitcher")) {
        profiles.forEach { profile ->
            val selected = profile.user == selectedUser
            SegmentedButtonItem(
                selected = selected,
                onClick = { onSelect(profile.user) },
                icon = if (selected) {
                    { Icon(imageVector = BlockerIcons.CheckSmall, contentDescription = null) }
                } else {
                    null
                },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = profile.label.text(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!profile.running) {
                            Text(
                                text = stringResource(string.core_ui_profile_not_running),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** The name the user sees for a profile in a switcher or a question about it. */
@Composable
fun ProfileLabel.text(): String = when (this) {
    ProfileLabel.Personal -> stringResource(string.core_ui_profile_personal)
    ProfileLabel.Work -> stringResource(string.core_ui_profile_work)
    is ProfileLabel.Named -> name
    is ProfileLabel.Numbered -> stringResource(string.core_ui_profile_user, id)
}

@PreviewThemes
@Composable
private fun ProfileSwitcherPreview() {
    BlockerTheme {
        Surface {
            ProfileSwitcher(
                profiles = listOf(
                    ProfileTab(UserId(0), ProfileLabel.Personal, running = true),
                    ProfileTab(UserId(10), ProfileLabel.Work, running = false),
                ),
                selectedUser = UserId(0),
                onSelect = {},
            )
        }
    }
}
