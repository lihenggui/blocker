// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.history.OperationHistoryRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.profileLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

internal class DeviceLabels(val profiles: Map<UserId, ProfileLabel>, private val apps: Map<AppKey, InstalledApp>) {
    private val appsByPackage = apps.values.groupBy { it.key.packageName }

    fun appLabel(packageName: String): String = appsByPackage[packageName]
        ?.firstNotNullOfOrNull { it.label.takeIf(String::isNotBlank) } ?: packageName

    fun appIcon(packageName: String): ApkIcon {
        val app = appsByPackage[packageName]?.firstOrNull()
        return ApkIcon(packageName, app?.apkPath, app?.versionCode ?: 0, app?.uid)
    }
}

internal fun OperationHistoryRepository.observeHistory(): Flow<Result<List<Operation>>> = observeOperations()
    .map { Result.success(it) }
    .catch {
        Timber.e(it, "Could not load operation history")
        emit(Result.failure(it))
    }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeDeviceLabels(
    deviceUsers: DeviceUserRepository,
    installedApps: InstalledAppRepository,
    ownUser: UserId,
): Flow<DeviceLabels> = deviceUsers.users
    .catch {
        Timber.w(it, "Could not read cached Android users")
        emit(emptyList())
    }
    .flatMapLatest { users ->
        val profiles = users.associate { it.id to it.profileLabel() }.toMutableMap()
        profiles.putIfAbsent(ownUser, ProfileLabel.Numbered(ownUser.value))
        val appFlows = (listOf(ownUser) + (profiles.keys - ownUser)).map { id ->
            installedApps.apps(id)
                .catch {
                    Timber.w(it, "Could not read cached apps for user %d", id.value)
                    emit(emptyList())
                }
        }
        combine(appFlows) { lists ->
            val apps = lists.asSequence().flatten().associateBy { it.key }
            DeviceLabels(profiles, apps)
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Result<List<Operation>>>.withRuleLabels(
    rules: GeneralRuleRepository,
): Flow<Pair<Result<List<Operation>>, Map<Int, String>>> = flatMapLatest { result ->
    val ids = result.getOrNull().orEmpty().asSequence().mapNotNull { it.ruleId }.distinct().toList()
    if (ids.isEmpty()) {
        flowOf(result to emptyMap())
    } else {
        val names = ids.map { id ->
            rules.getGeneralRule(id)
                .map { rule -> rule.name.takeIf(String::isNotBlank)?.let { id to it } }
                .onStart { emit(null) }
                .catch {
                    Timber.w(it, "Could not read history rule label for %d", id)
                    emit(null)
                }
        }
        combine(names) { labels -> result to labels.filterNotNull().toMap() }
    }
}

internal fun List<Operation>.historyRows(device: DeviceLabels, rules: Map<Int, String>): List<HistoryOperation> {
    val undone = filter { it.undoneByOperationId != null }.groupBy { it.undoneByOperationId }
    return map { it.historyRow(device, rules, undone[it.id].orEmpty()) }
}

internal fun Operation.historyRow(
    device: DeviceLabels,
    rules: Map<Int, String>,
    undoneOperations: List<Operation>,
): HistoryOperation {
    val target = when (undoneOperations.size) {
        0 -> null
        1 -> UndoTarget.Single(undoneOperations.single().headline(device, rules))
        else -> UndoTarget.Several(undoneOperations.size)
    }
    return HistoryOperation(
        operation = this,
        headline = headline(device, rules),
        appIcon = when (source) {
            OperationSource.APP_DETAIL, OperationSource.DEBLOATER -> ref?.takeIf(String::isNotBlank)?.let(device::appIcon)
            else -> null
        },
        undoTarget = target,
    )
}

private fun Operation.headline(device: DeviceLabels, rules: Map<Int, String>): OperationHeadline = OperationHeadline(
    source = source,
    reference = when (source) {
        OperationSource.APP_DETAIL, OperationSource.DEBLOATER, OperationSource.EXTERNAL_PROVIDER ->
            ref?.takeIf(String::isNotBlank)?.let(device::appLabel)

        OperationSource.RULE_DETAIL -> ruleId?.let(rules::get)

        else -> null
    },
)

private val Operation.ruleId: Int?
    get() = ref?.toIntOrNull()?.takeIf { source == OperationSource.RULE_DETAIL }
