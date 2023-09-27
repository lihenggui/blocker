/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.R

/**
 * Blocker icons. Material icons are [ImageVector]s, custom icons are drawable resource IDs.
 */
object BlockerIcons {
    val Apps = Icons.Outlined.Apps
    val GeneralRule = Icons.Outlined.CollectionsBookmark
    val Sort = Icons.AutoMirrored.Outlined.Sort
    val Clear = Icons.Outlined.Clear
    val SelectAll = Icons.Outlined.SelectAll
    val Inbox = Icons.Outlined.Inbox
    val ExpandMore = Icons.Outlined.ExpandMore
    val ExpandLess = Icons.Outlined.ExpandLess
    val Block = Icons.Outlined.Block
    val CheckCircle = Icons.Outlined.CheckCircle
    val Folder = Icons.Outlined.Folder
    val Search = Icons.Outlined.Search
    val BugReport = Icons.Outlined.BugReport
    val List = Icons.AutoMirrored.Outlined.List
    val AutoFix = Icons.Outlined.AutoFixHigh
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Close = Icons.Outlined.Close
    val Rule = Icons.AutoMirrored.Outlined.Rule
    val Deselect = Icons.Outlined.Deselect
    val SubdirectoryArrowRight = Icons.Outlined.SubdirectoryArrowRight
    val Error = Icons.Outlined.Error
    val DesignService = Icons.Outlined.DesignServices
    val DocumentScanner = Icons.Outlined.DocumentScanner
    val Share = Icons.Outlined.IosShare
    val CheckList = Icons.Outlined.Checklist
    val CheckSmall = Icons.Outlined.Check
    val Language = Icons.Default.Language

    val Rectangle = R.drawable.core_designsystem_ic_rectangle
    val Android = R.drawable.core_designsystem_ic_android
    val GitHub = R.drawable.core_designsystem_ic_github
    val Telegram = R.drawable.core_designsystem_ic_telegram

    val ArrowDropDown = Icons.Rounded.ArrowDropDown
    val ArrowDropUp = Icons.Rounded.ArrowDropUp
    val Check = Icons.Rounded.Check
    val MoreVert = Icons.Default.MoreVert
    val ShortText = Icons.AutoMirrored.Rounded.ShortText
    val ViewDay = Icons.Rounded.ViewDay
}

/**
 * A sealed class to make dealing with [ImageVector] and [DrawableRes] icons easier.
 */
sealed class Icon {
    data class ImageVectorIcon(val imageVector: ImageVector) : Icon()
    data class DrawableResourceIcon(@DrawableRes val id: Int) : Icon()
}

@Composable
fun BlockerActionIcon(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        modifier = modifier,
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
    )
}

@Composable
fun BlockerDisplayIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.outline,
) {
    Icon(
        modifier = Modifier
            .size(96.dp)
            .padding(8.dp),
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
    )
}
