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
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.core.ifw.IfwStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

// The order the earlier exporter wrote components in, kept so files stay easy to compare.
private val TYPE_ORDER = listOf(ComponentType.RECEIVER, ComponentType.SERVICE, ComponentType.ACTIVITY, ComponentType.PROVIDER)

/**
 * Backs up the component state of one user's apps as Blocker rule files in [BackupSet] layout.
 *
 * Each app is read from the system first. A file lists, for every activity, service and receiver,
 * an [ControllerType.IFW] entry whose state is whether no unconditional Intent Firewall rule
 * blocks it, then a [ControllerType.PM] entry whose state is whether PackageManager does not have
 * it disabled; for a provider only the PM entry. An app without components gets no file.
 *
 * When the firewall rules cannot be reached at all, the run leaves IFW entries out and says so,
 * and an existing backup of an app keeps its IFW entries; only its PM entries are replaced. When
 * one app's rule file is damaged, that app is skipped and its existing backup left as it is.
 */
class BlockerRuleExporter @Inject constructor(
    private val installedApps: InstalledAppRepository,
    private val components: ComponentStatusRepository,
    private val channel: PrivilegedChannel,
    private val ifwStore: IfwStore,
    private val json: Json,
    @AppPackageName private val appPackageName: String,
    private val selectedUsers: SelectedUserRepository,
) {
    /**
     * Writes the backup of [user]'s apps into [user]'s set in [storage]: every app, or third-party
     * apps only unless [includeSystemApps], or only [packageName] when given, whatever kind of
     * app it is. Connects the privileged channel first, which may show the su or Shizuku prompt.
     */
    suspend fun export(
        storage: BackupStorage,
        user: UserId,
        packageName: String? = null,
        includeSystemApps: Boolean = false,
        onProgress: RuleRunProgress = { _, _, _ -> },
    ): RuleRunResult {
        // A backup of the user Blocker runs in works without privilege, with PM entries only.
        channel.connect()
        // Older controllers write the rule files behind the store's back.
        ifwStore.clearCache()
        val summary = SummaryBuilder()
        val apps = when (val listed = appsToExport(user, packageName, includeSystemApps, summary)) {
            is ReadResult.Success -> listed.value
            is ReadResult.Failure -> return RuleRunResult.Failed(listed.reason.reason)
        }
        summary.firewallUnavailable = !components.firewallReadable.first()
        if (summary.firewallUnavailable) Timber.w("IFW rules cannot be reached, leave IFW entries out of the backup")
        val set = BackupSet(user)
        val (own, exportable) = apps.partition { it.key == AppKey(appPackageName, selectedUsers.ownUser) }
        summary.skip(SkipReason.PROTECTED_COMPONENT, own.size)
        exportable.forEachIndexed { index, app ->
            onProgress(app.key.packageName, index, exportable.size)
            exportApp(storage, set, app, summary)
        }
        return RuleRunResult.Completed(summary.build())
    }

    private suspend fun appsToExport(
        user: UserId,
        packageName: String?,
        includeSystemApps: Boolean,
        summary: SummaryBuilder,
    ): ReadResult<List<InstalledApp>> {
        if (packageName != null) {
            return when (val read = installedApps.refreshApp(AppKey(packageName, user))) {
                is ReadResult.Success -> ReadResult.Success(listOfNotNull(read.value)).also { if (read.value == null) summary.skip(SkipReason.APP_NOT_INSTALLED) }
                is ReadResult.Failure -> read
            }
        }
        return when (val read = installedApps.installedApps(user)) {
            is ReadResult.Success -> {
                val (system, others) = read.value.values.partition { it.isSystem }
                if (!includeSystemApps) summary.skip(SkipReason.SYSTEM_APP, system.size)
                ReadResult.Success((if (includeSystemApps) system + others else others).sortedBy { it.key.packageName })
            }

            is ReadResult.Failure -> read
        }
    }

    private suspend fun exportApp(storage: BackupStorage, set: BackupSet, app: InstalledApp, summary: SummaryBuilder) {
        val packageName = app.key.packageName
        val statuses = when (val read = components.freshComponents(app.key)) {
            is ReadResult.Success -> read.value

            is ReadResult.Failure -> {
                if (read.reason is ReadFailure.PackageNotFound) summary.skip(SkipReason.APP_NOT_INSTALLED) else summary.fail(FailureReason.STATE_UNREADABLE)
                return
            }
        }
        if (statuses.isEmpty()) {
            summary.skip(SkipReason.NO_COMPONENTS)
            return
        }
        if (statuses.any { it.state.pm == PmSetting.UNKNOWN }) {
            Timber.w("Cannot read the PM settings of ${app.key}, keep its backup")
            summary.fail(FailureReason.STATE_UNREADABLE)
            return
        }
        val withIfw = !summary.firewallUnavailable
        if (withIfw && statuses.any { it.declaration.type.isFilteredByIntentFirewall && it.state.ifw == IfwBlock.UNKNOWN }) {
            Timber.w("Cannot read the IFW rules of $packageName, keep its backup")
            summary.fail(FailureReason.RULES_UNREADABLE)
            return
        }
        val path = set.fileOf(packageName)
        val entries = if (withIfw) {
            backupEntries(statuses, withIfw = true)
        } else {
            val kept = keptIfwEntries(storage, path, packageName) ?: run {
                summary.fail(FailureReason.BACKUP_UNREADABLE)
                return
            }
            withKeptIfwEntries(backupEntries(statuses, withIfw = false), kept)
        }
        val rule = BlockerRule(
            packageName = packageName,
            versionName = app.versionName,
            versionCode = app.versionCode,
            components = entries.toMutableList(),
        )
        try {
            storage.write(path, json.encodeToString(BlockerRule.serializer(), rule))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot write the backup of $packageName")
            summary.fail(FailureReason.WRITE_FAILED)
            return
        }
        summary.apps++
        summary.applied += entries.size
    }

    /**
     * The IFW entries of the backup already at [path], to keep while the rules cannot be read, or
     * null when that file cannot be read or parsed and so must not be replaced.
     */
    private suspend fun keptIfwEntries(storage: BackupStorage, path: String, packageName: String): List<ComponentRule>? {
        val text = try {
            storage.read(path) ?: return emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the existing backup of $packageName")
            return null
        }
        return try {
            json.decodeFromString(BlockerRule.serializer(), text).components.filter { it.method == ControllerType.IFW }
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            Timber.e(e, "Cannot parse the existing backup of $packageName, keep it")
            null
        }
    }
}

/**
 * The backup entries of [statuses]: for each component its IFW entry when [withIfw] and it is
 * filtered, then its PM entry, ordered by type as earlier versions wrote them, then by class name.
 */
internal fun backupEntries(statuses: List<ComponentStatus>, withIfw: Boolean): List<ComponentRule> = statuses
    .sortedWith(compareBy({ TYPE_ORDER.indexOf(it.declaration.type) }, { it.declaration.id.className }))
    .flatMap { status ->
        val (id, type) = status.declaration.id to status.declaration.type
        buildList {
            if (withIfw && type.isFilteredByIntentFirewall) {
                add(ComponentRule(id.packageName, id.className, status.state.ifw != IfwBlock.BLOCKED, type, ControllerType.IFW))
            }
            add(ComponentRule(id.packageName, id.className, status.state.pm != PmSetting.DISABLED, type, ControllerType.PM))
        }
    }

/**
 * [pmEntries] with each component's [kept] IFW entry in front of its PM entry, as a full backup
 * orders them; kept entries of components no longer declared come last.
 */
internal fun withKeptIfwEntries(pmEntries: List<ComponentRule>, kept: List<ComponentRule>): List<ComponentRule> {
    val byName = kept.groupBy { it.name }
    val placed = mutableSetOf<String>()
    val merged = pmEntries.flatMap { entry ->
        val ifw = byName[entry.name].orEmpty().takeIf { placed.add(entry.name) }.orEmpty()
        ifw + entry
    }
    return merged + kept.filter { it.name !in placed }
}
