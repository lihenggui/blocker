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

package com.merxury.blocker.core.domain.rule

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.InstalledApp
import kotlin.time.Instant

internal val OWNER = UserId(0)
internal val WORK = UserId(10)

/** The package name the matcher is told Blocker has. */
internal const val SELF_PACKAGE = "com.merxury.blocker.test"

internal fun installedApp(packageName: String, user: UserId = OWNER, isSystem: Boolean = false) = InstalledApp(
    key = AppKey(packageName, user),
    uid = 10_000 + user.value * UIDS_PER_USER,
    label = packageName,
    versionName = "1.0",
    versionCode = 1,
    isSystem = isSystem,
    enabled = true,
    enabledSetting = AppEnabledSetting.DEFAULT,
    apkPath = "/data/app/$packageName/base.apk",
    dataDir = "/data/user/${user.value}/$packageName",
    firstInstallTime = Instant.fromEpochMilliseconds(1_000),
    lastUpdateTime = Instant.fromEpochMilliseconds(2_000),
    minSdk = 21,
    targetSdk = 34,
)

internal fun status(
    packageName: String,
    className: String,
    type: ComponentType = ComponentType.RECEIVER,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
) = ComponentStatus(
    declaration = ComponentDeclaration(ComponentId(packageName, className), type, exported = false, enabledInManifest = true),
    state = ControlState(pm, ifw),
)

/**
 * Rules and components on which matching is checked: plain keywords, ASCII and non-ASCII case,
 * `_` and `%` as literal characters, a keyword shared by two rules, a rule with no keywords and one that
 * matches nothing.
 */
internal object MatchingData {
    val rules = listOf(
        GeneralRule(id = 1, name = "Tracker", searchKeyword = listOf("com.tracker.", "TrackerService")),
        GeneralRule(id = 2, name = "Shared keyword", searchKeyword = listOf("com.tracker.")),
        GeneralRule(id = 3, name = "Ads", searchKeyword = listOf("COM.ADS.SDK")),
        GeneralRule(id = 4, name = "Literal underscore", searchKeyword = listOf("push_kit")),
        GeneralRule(id = 5, name = "Literal percent", searchKeyword = listOf("analytics%Receiver")),
        GeneralRule(id = 6, name = "Non-ASCII", searchKeyword = listOf("ÜBERSICHT")),
        GeneralRule(id = 7, name = "Nothing", searchKeyword = listOf("does.not.exist")),
        GeneralRule(id = 8, name = "No keywords", searchKeyword = emptyList()),
        GeneralRule(id = 9, name = "Short", searchKeyword = listOf("Sync")),
    )

    val apps = listOf(
        installedApp("com.alpha"),
        installedApp("com.beta"),
        installedApp("com.gamma"),
        installedApp("com.system", isSystem = true),
    )

    val components = listOf(
        status("com.alpha", "com.tracker.TrackerService", ComponentType.SERVICE),
        status("com.alpha", "com.tracker.Other", pm = PmSetting.DISABLED),
        status("com.alpha", "com.ads.sdk.AdActivity", ComponentType.ACTIVITY),
        status("com.alpha", "com.alpha.SyncAdapter", ComponentType.SERVICE),
        status("com.beta", "com.huawei.push.kit.Receiver"),
        status("com.beta", "com.huawei.pushXkit.Receiver"),
        status("com.beta", "com.huawei.push__kit.Receiver"),
        status("com.beta", "com.huawei.PUSH_Kit.Receiver"),
        status("com.beta", "com.beta.analytics.core.BootReceiver", ifw = IfwBlock.BLOCKED),
        status("com.beta", "com.beta.Analytics.Receiver"),
        status("com.gamma", "com.gamma.übersicht.MainActivity", ComponentType.ACTIVITY),
        status("com.gamma", "com.gamma.MyTrackerService", ComponentType.SERVICE),
        status("com.gamma", "com.gamma.Plain", ComponentType.PROVIDER),
        status("com.system", "com.tracker.SystemTracker", ComponentType.SERVICE),
        status("com.system", "com.ads.sdk.SystemAd"),
    )
}
