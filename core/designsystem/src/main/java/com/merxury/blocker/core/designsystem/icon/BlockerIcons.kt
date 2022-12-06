/*
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Grid3x3
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.merxury.blocker.core.designsystem.R

/**
 * Now in Android icons. Material icons are [ImageVector]s, custom icons are drawable resource IDs.
 */
object BlockerIcons {
    val List = Icons.Outlined.List
    val Search = Icons.Outlined.Search
    val Settings = Icons.Outlined.Settings
    val More = Icons.Outlined.MoreVert
    val BugReport = Icons.Outlined.BugReport
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
    val Export = R.drawable.ic_export
    val Import = R.drawable.ic_import
    val Android = R.drawable.ic_android
    val Git = R.drawable.ic_git
    val GitHub = R.drawable.ic_github

    val AccountCircle = Icons.Outlined.AccountCircle
    val Add = Icons.Rounded.Add
    val ArrowBack = Icons.Rounded.ArrowBack
    val ArrowDropDown = Icons.Rounded.ArrowDropDown
    val ArrowDropUp = Icons.Rounded.ArrowDropUp
    val Bookmark = R.drawable.ic_bookmark
    val BookmarkBorder = R.drawable.ic_bookmark_border
    val Bookmarks = R.drawable.ic_bookmarks
    val BookmarksBorder = R.drawable.ic_bookmarks_border
    val Check = Icons.Rounded.Check
    val ExpandLess = Icons.Rounded.ExpandLess
    val Fullscreen = Icons.Rounded.Fullscreen
    val Grid3x3 = Icons.Rounded.Grid3x3
    val MenuBook = R.drawable.ic_menu_book
    val MenuBookBorder = R.drawable.ic_menu_book_border
    val MoreVert = Icons.Default.MoreVert
    val Person = Icons.Rounded.Person
    val PlayArrow = Icons.Rounded.PlayArrow
    val ShortText = Icons.Rounded.ShortText
    val Tag = Icons.Rounded.Tag
    val Upcoming = R.drawable.ic_upcoming
    val UpcomingBorder = R.drawable.ic_upcoming_border
    val ViewDay = Icons.Rounded.ViewDay
    val VolumeOff = Icons.Rounded.VolumeOff
    val VolumeUp = Icons.Rounded.VolumeUp
}

/**
 * A sealed class to make dealing with [ImageVector] and [DrawableRes] icons easier.
 */
sealed class Icon {
    data class ImageVectorIcon(val imageVector: ImageVector) : Icon()
    data class DrawableResourceIcon(@DrawableRes val id: Int) : Icon()
}
