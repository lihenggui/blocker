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

package com.merxury.blocker.feature.appdetail.impl

import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.profile.ProfileLabel
import kotlin.time.Instant

/**
 * The header of the detail screen and the actions on the whole app.
 *
 * @property app the app as cached for its user; null while it is not cached
 * @property profile the profile the app belongs to when it is not the user Blocker runs in; null
 * also while the users are not cached yet
 * @property launch how the app can be opened from here
 * @property showLibChecker whether LibChecker can show the app: it is installed, and so is the app,
 * for the user Blocker runs in
 * @property hasCustomizedRules whether the user wrote descriptions for components of the app, which
 * can be shared
 * @property ruleWork what a rule work started here last reported; see [AppHeaderViewModel.onRuleWorkShown]
 * @property sharedRules the rules zipped to share; see [AppHeaderViewModel.onShareHandled]
 */
data class AppHeaderUiState(
    val app: HeaderApp? = null,
    val profile: ProfileLabel? = null,
    val launch: LaunchMode = LaunchMode.NONE,
    val showLibChecker: Boolean = false,
    val hasCustomizedRules: Boolean = false,
    val useDynamicColor: Boolean = false,
    val ruleWork: RuleWorkEvent? = null,
    val sharedRules: ZippedRule? = null,
)

/**
 * An app as the header and the app info tab show it.
 *
 * @property uid the app's uid in its user, or null when the system does not report it
 * @property dataDir the app's data directory in its user, or null when the system does not report it
 */
data class HeaderApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val icon: ApkIcon,
    val uid: Int?,
    val targetSdk: Int,
    val minSdk: Int,
    val lastUpdateTime: Instant,
    val dataDir: String?,
)

/** How tapping the app icon opens the app. */
enum class LaunchMode {
    /** Through its launch intent, in the user Blocker runs in. */
    OWN_USER,

    /** Through the launcher service, in a profile that shares the screen with Blocker's user. */
    PROFILE,

    /** It cannot be opened from here: Android shows no other user's apps to Blocker's user. */
    NONE,
}
