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

package com.merxury.blocker.core.data.device

import com.merxury.blocker.core.database.cache.AppEntity
import com.merxury.blocker.core.database.cache.ComponentCacheDao
import com.merxury.blocker.core.database.cache.asEntity
import com.merxury.blocker.core.database.cache.asExternalModel
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.isPackageRuleFileName
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Instant

/**
 * What one package refresh could read.
 * @property failures in the order app, declarations, settings, firewall; empty when all were read
 * @property pmSettings the settings read for the user, or null when they could not be read
 * @property pmFailure why the settings of the app's components are not known: their read failed, or
 * there were no declarations to read them for
 * @property declarationsCurrent whether the settings were read for the app's current declarations:
 * read now, or cached for its current version. False when the app or its declarations could not be
 * read and the cached ones were used.
 */
internal data class PackageRefresh(
    val failures: List<ReadFailure>,
    val pmSettings: Map<ComponentId, PmSetting>?,
    val pmFailure: ReadFailure?,
    val declarationsCurrent: Boolean = false,
) {
    val removed: Boolean get() = failures.any { it is ReadFailure.PackageNotFound }

    fun asResult(): ReadResult<Unit> = failures.firstOrNull()?.let { ReadResult.Failure(it) } ?: ReadResult.Success(Unit)

    companion object {
        fun removed(key: AppKey): PackageRefresh {
            val failure = ReadFailure.PackageNotFound(key)
            return PackageRefresh(listOf(failure), null, failure)
        }
    }
}

/**
 * Whether the channel's identity can reach the Intent Firewall rule files. Without it the rules
 * are unknown by design, not because a read failed.
 */
internal val ChannelState.allowsFirewallReads: Boolean
    get() = this is ChannelState.Ready && identity.canWriteSystemFiles

/**
 * Reads the component state of one package from the system and writes it to the cache. Callers
 * hold the package's lock; see [LocalComponentStatusRepository].
 */
internal class PackageStateReader @Inject constructor(
    private val dao: ComponentCacheDao,
    private val reader: SystemPackageReader,
    private val ifwStore: IfwStore,
    private val channel: PrivilegedChannel,
    private val installedApps: LocalInstalledAppRepository,
) {
    // Declarations are shared. The next complete read can come from any user with the package.
    private val declarationRetries = ConcurrentHashMap.newKeySet<String>()

    fun needsDeclarationRetry(packageName: String): Boolean = packageName in declarationRetries

    suspend fun forgetDeclarationRetryIfUncached(packageName: String) {
        if (needsDeclarationRetry(packageName) && dao.getDeclarations(packageName).isEmpty()) declarationRetries -= packageName
    }

    /**
     * Reads every part first, then writes them in one transaction, in the order the cache
     * requires: declarations, then settings, then firewall state, so an observer never sees part
     * of a refresh. The reads stop on cancellation with nothing written; once writing starts it
     * finishes.
     *
     * @param app the app as just read, or null when it could not be read ([appFailure] says why);
     * then the cached declarations are used as they are
     */
    suspend fun refresh(
        key: AppKey,
        app: InstalledApp?,
        appFailure: ReadFailure?,
        automatic: Boolean = false,
        previous: AppEntity? = null,
        forceDeclarations: Boolean = false,
    ): PackageRefresh {
        val packageName = key.packageName
        val failures = listOfNotNull(appFailure).toMutableList()
        val cached = dao.getDeclarations(packageName).map { it.asExternalModel() }
        val marker = dao.scanMarker(packageName)
        val ifwWasReadable = channel.state.value.allowsFirewallReads
        val sameVersion = app != null && scanIsForVersion(app, previous, marker)
        val retryDeclarations = needsDeclarationRetry(packageName)
        var verifiedDeclarations = false
        val fresh = if (app != null && (forceDeclarations || retryDeclarations || !sameVersion || marker != app.lastUpdateTime)) {
            // An unlock during the read cannot establish that the read started unlocked.
            val unlockedBefore = (retryDeclarations || (sameVersion && cached.isNotEmpty())) && isRunningAndUnlocked(key.userId)
            when (val read = reader.declarations(key)) {
                is ReadResult.Success -> {
                    val shrank = sameVersion && read.value.distinctBy { it.id }.size < cached.size
                    verifiedDeclarations = unlockedBefore && (shrank || retryDeclarations) && isRunningAndUnlocked(key.userId)
                    if (shrank && !verifiedDeclarations) {
                        declarationRetries += packageName
                        Timber.w("Defer smaller declaration list for $key at version ${app.versionCode}: user is not known to be running and unlocked")
                        return aborted(ReadFailure.ManifestUnreadable(packageName))
                    }
                    read.value
                }

                is ReadResult.Failure -> {
                    if (read.reason is ReadFailure.PackageNotFound) return remove(key)
                    Timber.w("Cannot read the declarations of $key, keep the cached ones: ${read.reason}")
                    failures += read.reason
                    null
                }
            }
        } else {
            null
        }
        if (automatic && failures.isNotEmpty()) return aborted(failures.first())

        val declarations = fresh ?: cached
        val declarationsCurrent = app != null && failures.isEmpty()
        var pmFailure: ReadFailure? = if (declarations.isEmpty()) failures.lastOrNull() else null
        val pm = if (declarations.isEmpty()) {
            emptyMap()
        } else {
            when (val read = reader.pmSettings(key, declarations.map { it.id })) {
                is ReadResult.Success -> read.value

                is ReadResult.Failure -> {
                    if (read.reason is ReadFailure.PackageNotFound) return remove(key)
                    if (automatic) return aborted(read.reason)
                    Timber.w("Cannot read the PM settings of $key, store them as unknown: ${read.reason}")
                    failures += read.reason
                    pmFailure = read.reason
                    null
                }
            }
        }
        if (automatic && declarations.any { pm?.get(it.id).let { setting -> setting == null || setting == PmSetting.UNKNOWN } }) {
            Timber.w("PackageManager did not return all component settings for $key")
            return aborted(ReadFailure.ChannelUnavailable)
        }
        val ifwReadable = channel.state.value.allowsFirewallReads
        val ifw = readIfw(packageName, declarations)
        if (ifw == null && (ifwWasReadable || ifwReadable || channel.state.value.allowsFirewallReads)) {
            val failure = ReadFailure.RulesUnreadable(packageName)
            if (automatic) return aborted(failure)
            failures += failure
        }
        val pmSettings = declarations.associate { it.id.className to (pm?.get(it.id) ?: PmSetting.UNKNOWN) }
        val ifwBlocks = ifwBlocksOf(declarations, ifw).filterValues { !automatic || it != IfwBlock.UNKNOWN }
        val entities = fresh?.map { it.asEntity() }
        val stored = withContext(NonCancellable) {
            if (automatic) {
                val failure = installedApps.storeRefresh(checkNotNull(app), previous, entities, pmSettings, ifwBlocks)
                if (failure != null) return@withContext aborted(failure)
            } else if (!dao.storePackageState(packageName, key.userId.value, entities, app?.lastUpdateTime?.takeIf { fresh != null }, pmSettings, ifwBlocks)) {
                Timber.i("$packageName was removed from the cache while it was read")
                forgetDeclarationRetryIfUncached(packageName)
                return@withContext PackageRefresh.removed(key)
            }
            if (verifiedDeclarations) declarationRetries -= packageName
            PackageRefresh(failures, pm, pmFailure, declarationsCurrent)
        }
        return stored
    }

    private suspend fun isRunningAndUnlocked(user: UserId): Boolean = when (val read = reader.users()) {
        is ReadResult.Success -> read.value.any { it.id == user && it.running && it.unlocked == true }
        is ReadResult.Failure -> false
    }

    private suspend fun scanIsForVersion(app: InstalledApp, previous: AppEntity?, marker: Instant?): Boolean {
        if (marker == null) return false
        val name = app.key.packageName
        if (previous?.versionCode == app.versionCode && previous.lastUpdateTime == marker) return true
        if (dao.hasAppRevision(name, app.versionCode, marker)) return true
        // A same-version reinstall can move all app timestamps past the scan. An observed
        // version change instead invalidates the old marker before updating an app row.
        return (previous == null || previous.versionCode == app.versionCode) && !dao.hasAppAtUpdateTime(name, marker)
    }

    private fun aborted(failure: ReadFailure) = PackageRefresh(listOf(failure), null, failure)

    /** Re-reads the firewall state of the cached declarations of [packageName] and stores it. */
    suspend fun refreshIfw(packageName: String) {
        val declarations = dao.getDeclarations(packageName).map { it.asExternalModel() }
        if (declarations.isEmpty()) return
        val ifw = readIfw(packageName, declarations)
        if (ifw == null) Timber.w("Cannot read the changed IFW rules of $packageName, store them as unknown")
        withContext(NonCancellable) { dao.upsertIfwBlocks(packageName, ifwBlocksOf(declarations, ifw)) }
    }

    /**
     * The firewall state of [declarations], or null when the rules cannot be read. Providers are
     * not filtered, so they are always [IfwBlock.NOT_BLOCKED].
     */
    private suspend fun readIfw(packageName: String, declarations: List<ComponentDeclaration>): Map<ComponentId, IfwBlock>? {
        val filtered = declarations.filter { it.type.isFilteredByIntentFirewall }
        if (filtered.isEmpty()) return declarations.associate { it.id to IfwBlock.NOT_BLOCKED }
        if (!isPackageRuleFileName(packageName)) return null
        val blocks = ifwStore.blocks(packageName, declarations)
        // The store answers UNKNOWN for every filtered component when it cannot read the rules.
        return blocks.takeIf { filtered.none { blocks[it.id] == null || blocks[it.id] == IfwBlock.UNKNOWN } }
    }

    /** The firewall state to store for [declarations]: [ifw], or unknown where it was not read. */
    private fun ifwBlocksOf(declarations: List<ComponentDeclaration>, ifw: Map<ComponentId, IfwBlock>?): Map<String, IfwBlock> = declarations.associate { declaration ->
        val block = when {
            !declaration.type.isFilteredByIntentFirewall -> IfwBlock.NOT_BLOCKED
            else -> ifw?.get(declaration.id) ?: IfwBlock.UNKNOWN
        }
        declaration.id.className to block
    }

    private suspend fun remove(key: AppKey): PackageRefresh {
        installedApps.remove(key)
        forgetDeclarationRetryIfUncached(key.packageName)
        return PackageRefresh.removed(key)
    }
}
