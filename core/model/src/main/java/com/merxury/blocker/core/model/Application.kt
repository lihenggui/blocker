/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.core.model

import android.content.pm.PackageInfo
import android.os.Parcelable
import java.util.Date
import kotlinx.parcelize.Parcelize

/**
 * Created by Mercury on 2017/12/30.
 * An entity class that describe simplified application information
 */
@Parcelize
data class Application(
    var packageName: String = "",
    var versionName: String? = "",
    var isEnabled: Boolean = false,
    var label: String = "",
    var firstInstallTime: Date? = null,
    var lastUpdateTime: Date? = null,
    var packageInfo: PackageInfo? = null,
) : Parcelable
