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

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.blockLayers
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores Blocker rule files from a [BackupSet] into a user, through [ComponentStatusRepository]
 * so each package is changed in batches under its lock and the result is read back.
 *
 * An entry's method picks the layer: [ControllerType.PM] and [ControllerType.SHIZUKU] the
 * PackageManager setting, [ControllerType.IFW] the Intent Firewall, which for a provider means its
 * PackageManager setting, and [ControllerType.IFW_PLUS_PM] both. `state=false` blocks that layer
 * and `state=true` clears it, leaving the other layer alone. The component's type comes from what
 * the installed app declares, never from the file; entries for components it does not declare are
 * skipped. A component already in the state an entry asks for counts as unchanged.
 *
 * A file is for the app it is named after, `<package>.json`, and is applied to that app only: a
 * file whose `packageName` differs is skipped whole, and an entry naming another package is
 * skipped, both as [SkipReason.OTHER_APP].
 *
 * Counts are of components, one per entry: a file skipped whole counts its entries. Only a file
 * that cannot be read counts once, as a file, and an app whose state cannot be read once, as an app.
 */
class BlockerRuleImporter @Inject constructor(
    private val installedApps: InstalledAppRepository,
    private val components: ComponentStatusRepository,
    private val channel: PrivilegedChannel,
    private val json: Json,
) {
    /**
     * Restores the files of [source] in [storage] into [target]: every file whose app is
     * installed for [target], third-party apps only unless [includeSystemApps], or only the file
     * of [packageName] when given, whatever kind of app it is. A file that cannot be read or
     * names an unknown value fails alone. Connects the privileged channel first, which may show
     * the su or Shizuku prompt, and fails for privilege when it is not ready.
     */
    suspend fun import(
        storage: BackupStorage,
        source: BackupSet,
        target: UserId,
        packageName: String? = null,
        includeSystemApps: Boolean = false,
        onProgress: RuleRunProgress = { _, _, _ -> },
    ): RuleRunResult {
        if (!channel.connectAllowing()) return RuleRunResult.Failed(FailureReason.NO_PRIVILEGE)
        val files = try {
            if (packageName != null) {
                listOf(source.fileOf(packageName))
            } else {
                storage.listFiles(source.folder).filter { it.endsWith(BLOCKER_RULE_EXTENSION) }.sorted().map { pathOf(source.folder, it) }
            }
        } catch (e: BackupStorageException) {
            Timber.e(e, "Cannot list the backup set ${source.folder}")
            return RuleRunResult.Failed(FailureReason.STORAGE_UNAVAILABLE)
        }
        val apps = when (val read = installedApps.installedApps(target)) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return RuleRunResult.Failed(read.reason.reason)
        }
        val summary = SummaryBuilder()
        files.forEachIndexed { index, path ->
            onProgress(path.substringAfterLast('/'), index, files.size)
            val rule = readRule(storage, path, summary) ?: return@forEachIndexed
            // The file is for the package it is named after, which is the selected app for one.
            val filePackage = path.substringAfterLast('/').removeSuffix(BLOCKER_RULE_EXTENSION)
            val entries = rule.components.size
            if (rule.packageName != filePackage) {
                Timber.w("The backup $path names ${rule.packageName}, leave it")
                summary.skip(SkipReason.OTHER_APP, entries)
                return@forEachIndexed
            }
            val app = apps[filePackage]
            when {
                app == null -> summary.skip(SkipReason.APP_NOT_INSTALLED, entries)
                packageName == null && app.isSystem && !includeSystemApps -> summary.skip(SkipReason.SYSTEM_APP, entries)
                else -> importRule(rule, app, summary)
            }
        }
        return RuleRunResult.Completed(summary.build())
    }

    /** The rule in the file at [path], or null when the file is missing or cannot be used. */
    private suspend fun readRule(storage: BackupStorage, path: String, summary: SummaryBuilder): BlockerRule? {
        val text = try {
            storage.read(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the backup $path")
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return null
        }
        // Only a file asked for by package can be missing; there is nothing to restore then.
        if (text == null) return null
        val rule = try {
            json.decodeFromString(BlockerRule.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException, raised for unknown enum names too, is an IllegalArgumentException.
            Timber.e(e, "Cannot parse the backup $path")
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return null
        }
        if (rule.packageName.isNullOrBlank()) {
            Timber.e("The backup $path names no package")
            summary.fail(FailureReason.BACKUP_UNREADABLE)
            return null
        }
        return rule
    }

    private suspend fun importRule(rule: BlockerRule, app: InstalledApp, summary: SummaryBuilder) {
        val statuses = when (val read = components.freshComponents(app.key)) {
            is ReadResult.Success -> read.value

            is ReadResult.Failure -> {
                if (read.reason is ReadFailure.PackageNotFound) summary.skip(SkipReason.APP_NOT_INSTALLED, rule.components.size) else summary.fail(FailureReason.STATE_UNREADABLE)
                return
            }
        }
        val requests = restoreRequests(rule, app.key, statuses, summary)
        val states = statuses.associate { it.declaration.id to it.state }.toMutableMap()
        val applied = requests.sumOf { components.control(it).countControlOutcomes(it.goal, states, summary) }
        if (applied > 0) summary.apps++
    }
}

/**
 * The requests that restore [rule] into [app], one per goal and layers, each in the file's order.
 * Entries naming another package are skipped as [SkipReason.OTHER_APP], entries for components the
 * app does not declare as [SkipReason.NOT_DECLARED]; a class starting with `.` is resolved against
 * the package, and an entry repeated with the same goal and layers counts once.
 *
 * `state=true` is an unblock, never an enable: a backup writes it for DEFAULT and ENABLED alike,
 * so it only clears a DISABLED setting and leaves a component the manifest ships disabled at
 * DEFAULT, as the backup recorded it.
 */
internal fun restoreRequests(
    rule: BlockerRule,
    app: AppKey,
    statuses: List<ComponentStatus>,
    summary: SummaryBuilder,
): List<ControlRequest> {
    val declared = statuses.associate { it.declaration.id to it.declaration }
    val batches = linkedMapOf<Pair<ControlGoal, Set<ControlLayer>>, LinkedHashSet<ComponentDeclaration>>()
    rule.components.forEach { entry ->
        if (entry.packageName.isNotBlank() && entry.packageName != app.packageName) {
            summary.skip(SkipReason.OTHER_APP)
            return@forEach
        }
        val declaration = ComponentId.unflatten("${app.packageName}/${entry.name}")?.let(declared::get)
        if (declaration == null) {
            summary.skip(SkipReason.NOT_DECLARED)
            return@forEach
        }
        val goal = if (entry.state) ControlGoal.UNBLOCK else ControlGoal.BLOCK
        // The methods map to the same layers a block with that controller type uses.
        batches.getOrPut(goal to entry.method.blockLayers) { linkedSetOf() } += declaration
    }
    return batches.map { (key, declarations) -> ControlRequest(app.userId, declarations.toList(), key.first, key.second) }
}
