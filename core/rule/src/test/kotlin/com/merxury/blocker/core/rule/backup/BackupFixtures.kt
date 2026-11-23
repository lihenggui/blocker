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

package com.merxury.blocker.core.rule.backup

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal const val APP = "com.example.app"
internal val OWNER = UserId.SYSTEM
internal val WORK = UserId(10)

/** The configuration the workers get from NetworkModule.providesNetworkJson, as BlockerRuleFormatTest repeats it. */
internal val backupJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

internal fun installedApp(
    packageName: String = APP,
    user: UserId = OWNER,
    isSystem: Boolean = false,
) = InstalledApp(
    key = AppKey(packageName, user),
    uid = null,
    label = packageName,
    versionName = "1.2.3",
    versionCode = 123,
    isSystem = isSystem,
    enabledSetting = AppEnabledSetting.DEFAULT,
    apkPath = null,
    firstInstallTime = Instant.fromEpochMilliseconds(0),
    lastUpdateTime = Instant.fromEpochMilliseconds(0),
    minSdk = 24,
    targetSdk = 34,
)

internal fun declaration(
    simpleName: String,
    type: ComponentType,
    packageName: String = APP,
    enabledInManifest: Boolean = true,
) = ComponentDeclaration(ComponentId(packageName, "$packageName.$simpleName"), type, exported = true, enabledInManifest = enabledInManifest)

internal fun status(
    declaration: ComponentDeclaration,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
) = ComponentStatus(declaration, ControlState(pm, ifw))

internal val BOOT = declaration("BootReceiver", ComponentType.RECEIVER)
internal val SYNC = declaration("SyncService", ComponentType.SERVICE)
internal val MAIN = declaration("MainActivity", ComponentType.ACTIVITY)
internal val DATA = declaration("DataProvider", ComponentType.PROVIDER)

/** A real rule store over [fileSystem], so tests run the rule semantics the app uses. */
internal fun storeOver(fileSystem: FakeIfwFileSystem) = IntentFirewallStore(fileSystem, IfwXmlSerializer(), IfwXmlDeserializer())

internal fun resource(name: String): String = checkNotNull(BackupSet::class.java.classLoader?.getResource(name)) {
    "Missing test resource $name"
}.readText()

internal val RuleRunResult.summary: RuleRunSummary get() = (this as RuleRunResult.Completed).summary
