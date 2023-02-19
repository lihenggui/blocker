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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.ui.graphics.vector.ImageVector
import com.merxury.blocker.core.designsystem.R

/**
 * Blocker icons. Material icons are [ImageVector]s, custom icons are drawable resource IDs.
 */
object BlockerIcons {
    val Apps = Icons.Outlined.Apps
    val Article = Icons.Outlined.Article
    val GeneralRule = Icons.Outlined.CollectionsBookmark
    val Sort = Icons.Outlined.Sort
    val Share = Icons.Outlined.Share
    val Find = Icons.Outlined.FindInPage
    val Clear = Icons.Outlined.Clear
    val SelectAll = Icons.Outlined.SelectAll
    val Inbox = Icons.Outlined.Inbox
    val ExpandMore = Icons.Outlined.ExpandMore
    val ExpandLess = Icons.Outlined.ExpandLess
    val Tune = Icons.Outlined.Tune
    val Block = Icons.Outlined.Block
    val CheckCircle = Icons.Outlined.CheckCircle
    val Folder = Icons.Outlined.Folder
    val Search = Icons.Outlined.Search
    val BugReport = Icons.Outlined.BugReport
    val List = Icons.Outlined.List
    val Settings = Icons.Outlined.Settings
    val More = Icons.Outlined.MoreVert
    val AutoFix = Icons.Outlined.AutoFixHigh
    val Back = Icons.Outlined.ArrowBack
    val Close = Icons.Outlined.Close
    val CloudDownload = Icons.Outlined.CloudDownload
    val CloudUpload = Icons.Outlined.CloudUpload
    val Edit = Icons.Outlined.Edit
    val BackUp = Icons.Outlined.PhonelinkSetup
    val Filter = Icons.Outlined.FilterAlt
    val NewFolder = Icons.Outlined.CreateNewFolder
    val RocketLaunch = Icons.Outlined.RocketLaunch
    val LightBulb = Icons.Outlined.Lightbulb
    val NoApp = Icons.Outlined.EventBusy
    val PlayCircle = Icons.Outlined.PlayCircle
    val Restart = Icons.Outlined.RestartAlt
    val Save = Icons.Outlined.Save
    val CheckBox = Icons.Outlined.CheckBox
    val CheckBoxBlank = Icons.Outlined.CheckBoxOutlineBlank
    val Rule = Icons.Outlined.Rule
    val Deselect = Icons.Outlined.Deselect
    val SubdirectoryArrowRight = Icons.Outlined.SubdirectoryArrowRight
    val Error = Icons.Outlined.Error

    val Rectangle = R.drawable.ic_rectangle
    val Export = R.drawable.ic_export
    val Import = R.drawable.ic_import
    val Android = R.drawable.ic_android
    val GitHub = R.drawable.ic_github
    val Telegram = R.drawable.ic_telegram

    val ArrowDropDown = Icons.Rounded.ArrowDropDown
    val ArrowDropUp = Icons.Rounded.ArrowDropUp
    val Check = Icons.Rounded.Check
    val MoreVert = Icons.Default.MoreVert
    val ShortText = Icons.Rounded.ShortText
    val ViewDay = Icons.Rounded.ViewDay
}

/**
 * A sealed class to make dealing with [ImageVector] and [DrawableRes] icons easier.
 */
sealed class Icon {
    data class ImageVectorIcon(val imageVector: ImageVector) : Icon()
    data class DrawableResourceIcon(@DrawableRes val id: Int) : Icon()
}
