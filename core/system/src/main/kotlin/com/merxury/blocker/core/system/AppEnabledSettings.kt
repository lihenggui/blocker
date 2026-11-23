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

package com.merxury.blocker.core.system

import android.content.pm.PackageManager
import com.merxury.blocker.core.model.system.AppEnabledSetting

/**
 * The [AppEnabledSetting] for a `PackageManager.COMPONENT_ENABLED_STATE_*` value of a whole app.
 * Null, the answer when the setting cannot be read, and any other value are
 * [AppEnabledSetting.UNKNOWN].
 */
fun appEnabledSettingOf(state: Int?): AppEnabledSetting = when (state) {
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> AppEnabledSetting.DEFAULT

    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> AppEnabledSetting.ENABLED

    PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> AppEnabledSetting.DISABLED

    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> AppEnabledSetting.DISABLED_USER

    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> AppEnabledSetting.DISABLED_UNTIL_USED

    // Unreadable, for example because the app was removed after it was listed.
    else -> AppEnabledSetting.UNKNOWN
}
