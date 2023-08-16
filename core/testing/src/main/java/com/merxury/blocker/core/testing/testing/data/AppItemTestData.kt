/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.testing.testing.data

import com.merxury.blocker.core.ui.applist.model.AppItem
import kotlinx.datetime.Clock

val appInfoTestData = AppItem(
    label = "App",
    packageName = "com.merxury.blocker",
    versionName = "1.0.0",
    versionCode = 1,
    minSdkVersion = 33,
    targetSdkVersion = 21,
    isSystem = false,
    isRunning = false,
    isEnabled = true,
    firstInstallTime = Clock.System.now(),
    lastUpdateTime = Clock.System.now(),
    appServiceStatus =null,
    packageInfo = null,
    )