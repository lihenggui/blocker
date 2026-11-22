/*
 * Copyright 2025 Blocker
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

package com.merxury.core.ifw

import android.content.ComponentName
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Long enough that one probe covers the burst of per-component checks a screen makes,
// short enough that a root grant shows up on the next refresh.
private val ROOT_PROBE_RETRY_INTERVAL = 2.seconds

internal class IntentFirewall @Inject constructor(
    private val rootChecker: RootAvailabilityChecker,
    private val componentTypeResolver: ComponentTypeResolver,
    private val fileSystem: IfwFileSystem,
    private val serializer: IfwXmlSerializer,
    private val deserializer: IfwXmlDeserializer,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : IIntentFirewall {
    // Rules read from disk, keyed by package name. Only Missing and Loaded are cached,
    // so a failed read is retried instead of being mistaken for an empty file.
    private val cache = ConcurrentHashMap<String, StoredRules>()

    // Every file access for a package runs under its lock, so a read-modify-write
    // cannot interleave with another write or cache a file that is being replaced.
    private val packageLocks = ConcurrentHashMap<String, Mutex>()

    // Bumped by resetCache() so a read that overlaps an external change is not cached.
    private val cacheGeneration = AtomicLong()

    // After a probe finds root unavailable, getComponentEnableState() reports uncached packages
    // as not blocked without probing again until this mark passes. While root is not granted
    // each probe starts su, and callers check every component one by one. getRules() and
    // writes always probe.
    @Volatile
    private var skipRootProbeUntil: TimeMark? = null

    // ── Core API ───────────────────────────────────────────────────────

    override suspend fun getRules(packageName: String): IfwRules = when (val stored = loadRules(packageName)) {
        StoredRules.Missing -> IfwRules.empty()
        is StoredRules.Loaded -> stored.rules
        is StoredRules.Unreadable -> throw stored.error
    }

    override suspend fun saveRules(packageName: String, rules: IfwRules) {
        withPackageLock(packageName) { saveRulesLocked(packageName, rules) }
    }

    override suspend fun clear(packageName: String) {
        withPackageLock(packageName) { clearLocked(packageName) }
    }

    override fun resetCache() {
        Timber.d("Reset IFW cache")
        cacheGeneration.incrementAndGet()
        cache.clear()
        skipRootProbeUntil = null
    }

    // ── Component Filter Convenience Methods ───────────────────────────

    override suspend fun addComponentFilter(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        val ifwComponentType = resolveIfwComponentType(packageName, componentName) ?: return false
        val formattedName = formatName(packageName, componentName)
        Timber.i("Add component filter for $formattedName")

        return updateRules(packageName) { it.addComponentFilter(ifwComponentType, formattedName) }
    }

    override suspend fun removeComponentFilter(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        val ifwComponentType = resolveIfwComponentType(packageName, componentName) ?: return false
        val formattedName = formatName(packageName, componentName)
        Timber.i("Remove component filter for $formattedName")

        return updateRules(packageName) { it.removeComponentFilter(ifwComponentType, formattedName) }
    }

    override suspend fun addAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        Timber.i("Add component filters for ${list.size} components")
        updateComponentFilters(list, callback) { rules, type, name -> rules.addComponentFilter(type, name) }
    }

    override suspend fun removeAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        Timber.i("Remove component filters for ${list.size} components")
        updateComponentFilters(list, callback) { rules, type, name -> rules.removeComponentFilter(type, name) }
    }

    /**
     * Applies [change] to every component of a package in one [updateRules] call, so each
     * package is read, changed and written once under its lock. Component types are resolved
     * once per package before the lock is taken. [callback] runs only for packages whose rules
     * were saved or already matched, and outside the lock so it cannot deadlock on it.
     */
    private suspend fun updateComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
        change: (IfwRules, IfwComponentType, String) -> IfwRules,
    ) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        list.distinct().groupBy { it.packageName }.forEach { (packageName, components) ->
            val types = componentTypeResolver.getComponentTypes(packageName, components.map { it.className })
            val changes = components.mapNotNull { component ->
                val ifwType = types[component.className]?.toIfwComponentType()
                if (ifwType == null) {
                    Timber.d("Cannot change IFW rule for $component")
                    null
                } else {
                    component to ifwType
                }
            }
            if (changes.isEmpty()) return@forEach
            val saved = updateRules(packageName) { current ->
                changes.fold(current) { rules, (component, type) -> change(rules, type, component.flattenToString()) }
            }
            if (saved) changes.forEach { (component, _) -> callback(component) }
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        // Unreadable rules report the component as not blocked, as before. Callers have no
        // error path for this read, and writes refuse to touch an unreadable file.
        val cached = cache[packageName]
        if (cached == null && skipRootProbeUntil?.hasNotPassedNow() == true) return true
        val rules = (cached ?: loadRules(packageName)).rulesOrNull() ?: return true
        val formattedName = formatName(packageName, componentName)
        return IfwComponentType.entries.none { type ->
            formattedName in rules.componentFiltersFor(type)
        }
    }

    // ── Global Query ───────────────────────────────────────────────────

    override suspend fun getAllRules(): Map<String, IfwRules> {
        if (!rootChecker.isRootAvailable()) {
            Timber.v("Root unavailable, cannot list rules")
            return emptyMap()
        }
        val packages = fileSystem.listRuleFiles()
        // Unreadable files are left out, as before; writes to them are refused.
        return packages.associateWith { loadRules(it).rulesOrNull() ?: IfwRules.empty() }
            .filter { it.value.rules.isNotEmpty() }
    }

    // ── Private Helpers ────────────────────────────────────────────────

    private suspend inline fun <T> withPackageLock(packageName: String, action: () -> T): T = packageLocks.computeIfAbsent(packageName) { Mutex() }.withLock { action() }

    private suspend fun loadRules(packageName: String): StoredRules = cache[packageName] ?: withPackageLock(packageName) { loadRulesLocked(packageName) }

    private suspend fun loadRulesLocked(packageName: String): StoredRules {
        cache[packageName]?.let { return it }
        if (!rootChecker.isRootAvailable()) {
            Timber.v("Root unavailable, cannot load rules for $packageName")
            skipRootProbeUntil = timeSource.markNow() + ROOT_PROBE_RETRY_INTERVAL
            return StoredRules.Unreadable(RootUnavailableException())
        }
        skipRootProbeUntil = null
        val generation = cacheGeneration.get()
        val stored = try {
            val content = fileSystem.readRules(packageName)
            if (content == null) StoredRules.Missing else StoredRules.Loaded(deserializer.deserialize(content))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RootUnavailableException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read IFW rules for $packageName")
            return StoredRules.Unreadable(IOException("Cannot read IFW rules for $packageName", e))
        }
        if (cacheGeneration.get() == generation) {
            cache[packageName] = stored
            // resetCache() may have run between the check and the put.
            if (cacheGeneration.get() != generation) cache.remove(packageName)
        }
        return stored
    }

    /**
     * Applies [transform] to the current rules and saves the result under the package lock.
     * Skips the write when the rules already have the requested content.
     * Returns false without writing when the current rules cannot be read.
     */
    private suspend fun updateRules(packageName: String, transform: suspend (IfwRules) -> IfwRules): Boolean = withPackageLock(packageName) {
        val current = loadRulesLocked(packageName).rulesOrNull()
        if (current == null) {
            Timber.w("Leave IFW rules for $packageName untouched because they cannot be read")
            return@withPackageLock false
        }
        val updated = transform(current)
        if (updated != current) saveRulesLocked(packageName, updated)
        true
    }

    private suspend fun saveRulesLocked(packageName: String, rules: IfwRules) {
        if (rules.isEmpty()) {
            clearLocked(packageName)
            return
        }
        val xml = serializer.serialize(rules)
        cache.remove(packageName)
        fileSystem.writeRules(packageName, xml)
        Timber.i("Saved IFW rules for $packageName")
    }

    private suspend fun clearLocked(packageName: String) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.d("Clear IFW rule for $packageName")
        cache.remove(packageName)
        fileSystem.deleteRules(packageName)
    }

    private suspend fun resolveIfwComponentType(
        packageName: String,
        componentName: String,
    ): IfwComponentType? = componentTypeResolver.getComponentType(packageName, componentName).toIfwComponentType()

    private fun ComponentType.toIfwComponentType(): IfwComponentType? = when (this) {
        RECEIVER -> IfwComponentType.BROADCAST
        SERVICE -> IfwComponentType.SERVICE
        ACTIVITY -> IfwComponentType.ACTIVITY
        PROVIDER -> null
    }

    private fun formatName(packageName: String, name: String): String = "$packageName/$name"
}

/** What [IntentFirewall] found on disk for one package. Only [Missing] and [Loaded] are cached. */
private sealed interface StoredRules {
    /** No rule file exists, so the package has no rules. */
    data object Missing : StoredRules

    data class Loaded(val rules: IfwRules) : StoredRules

    /** Root is unavailable, or the file exists but cannot be read or parsed. Never overwritten. */
    data class Unreadable(val error: Exception) : StoredRules
}

private fun StoredRules.rulesOrNull(): IfwRules? = when (this) {
    StoredRules.Missing -> IfwRules.empty()
    is StoredRules.Loaded -> rules
    is StoredRules.Unreadable -> null
}

/**
 * Adds a component-filter to the rules for the given component type.
 * Returns a new [IfwRules] instance with the filter added.
 */
private fun IfwRules.addComponentFilter(
    componentType: IfwComponentType,
    name: String,
): IfwRules {
    val filter = IfwFilter.ComponentFilter(name)
    val existingRule = rulesFor(componentType).firstOrNull()
    return if (existingRule != null) {
        if (existingRule.filters.contains(filter)) return this
        val updatedRule = existingRule.copy(filters = existingRule.filters + filter)
        IfwRules(rules.map { if (it === existingRule) updatedRule else it })
    } else {
        IfwRules(rules + IfwRule(componentType = componentType, filters = listOf(filter)))
    }
}

/**
 * Removes a component-filter from the rules for the given component type.
 * Returns a new [IfwRules] instance with the filter removed. A rule that still has
 * intent filters is kept, because those block intents independently of the component.
 */
private fun IfwRules.removeComponentFilter(
    componentType: IfwComponentType,
    name: String,
): IfwRules {
    val filter = IfwFilter.ComponentFilter(name)
    val existingRule = rulesFor(componentType).firstOrNull() ?: return this
    if (filter !in existingRule.filters) return this
    val updatedFilters = existingRule.filters - filter
    return if (updatedFilters.isEmpty() && existingRule.intentFilters.isEmpty()) {
        IfwRules(rules.filter { it !== existingRule })
    } else {
        val updatedRule = existingRule.copy(filters = updatedFilters)
        IfwRules(rules.map { if (it === existingRule) updatedRule else it })
    }
}
