/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.extension

import android.content.pm.PackageManager.GET_ACTIVITIES
import android.content.pm.PackageManager.GET_CONFIGURATIONS
import android.content.pm.PackageManager.GET_DISABLED_COMPONENTS
import android.content.pm.PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS
import android.content.pm.PackageManager.GET_GIDS
import android.content.pm.PackageManager.GET_INSTRUMENTATION
import android.content.pm.PackageManager.GET_INTENT_FILTERS
import android.content.pm.PackageManager.GET_META_DATA
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.GET_PROVIDERS
import android.content.pm.PackageManager.GET_RECEIVERS
import android.content.pm.PackageManager.GET_SERVICES
import android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES
import android.content.pm.PackageManager.GET_SIGNATURES
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.GET_URI_PERMISSION_PATTERNS
import android.content.pm.PackageManager.MATCH_APEX
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
import android.content.pm.PackageManager.MATCH_SYSTEM_ONLY
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import androidx.annotation.IntDef

@Suppress("DEPRECATION")
@IntDef(
    GET_ACTIVITIES,
    GET_CONFIGURATIONS,
    GET_GIDS,
    GET_INSTRUMENTATION,
    GET_INTENT_FILTERS,
    GET_META_DATA,
    GET_PERMISSIONS,
    GET_PROVIDERS,
    GET_RECEIVERS,
    GET_SERVICES,
    GET_SHARED_LIBRARY_FILES,
    GET_SIGNATURES,
    GET_SIGNING_CERTIFICATES,
    GET_URI_PERMISSION_PATTERNS,
    MATCH_UNINSTALLED_PACKAGES,
    MATCH_DISABLED_COMPONENTS,
    MATCH_DISABLED_UNTIL_USED_COMPONENTS,
    MATCH_SYSTEM_ONLY,
    MATCH_APEX,
    GET_DISABLED_COMPONENTS,
    GET_DISABLED_UNTIL_USED_COMPONENTS,
    GET_UNINSTALLED_PACKAGES,
)
@Retention(AnnotationRetention.SOURCE)
annotation class PackageInfoFlags
