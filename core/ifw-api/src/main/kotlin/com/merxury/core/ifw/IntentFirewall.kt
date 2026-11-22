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

internal class IntentFirewall @Inject constructor(
    private val rootChecker: RootAvailabilityChecker,
    private val componentTypeResolver: ComponentTypeResolver,
    private val fileSystem: IfwFileSystem,
    private val serializer: IfwXmlSerializer,
    private val deserializer: IfwXmlDeserializer,
) : IIntentFirewall {
    // Rules read from disk, keyed by package name. Only Missing and Loaded are cached,
    // so a failed read is retried instead of being mistaken for an empty file.
    private val cache = ConcurrentHashMap<String, StoredRules>()

    // Every file access for a package runs under its lock, so a read-modify-write
    // cannot interleave with another write or cache a file that is being replaced.
    private val packageLocks = ConcurrentHashMap<String, Mutex>()

    // Bumped by resetCache() so a read that overlaps an external change is not cached.
    private val cacheGeneration = AtomicLong()

    // Packages last read while root was unavailable. A root probe may spawn su, so
    // getComponentEnableState() does not repeat it for every component of such a package.
    // getRules() and writes ignore this set. It is cleared once a read finds root available
    // or the cache is reset.
    private val packagesReadWithoutRoot: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
        packagesReadWithoutRoot.clear()
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
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.i("Add component filters for ${list.size} components")
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            val added = mutableListOf<ComponentName>()
            val saved = updateRules(packageName) { current ->
                var rules = current
                groupedMap[packageName]?.forEach componentLoop@{ component ->
                    val type = componentTypeResolver.getComponentType(packageName, component.className)
                    if (type == PROVIDER) {
                        Timber.d("Cannot add IFW rule for $component")
                        return@componentLoop
                    }
                    val ifwType = type.toIfwComponentType() ?: return@componentLoop
                    val formattedName = component.flattenToString()
                    rules = rules.addComponentFilter(ifwType, formattedName)
                    added += component
                }
                rules
            }
            // Report only saved changes, and outside the lock so callbacks cannot deadlock on it.
            if (saved) added.forEach { callback(it) }
        }
    }

    override suspend fun removeAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.i("Remove component filters for ${list.size} components")
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            val removed = mutableListOf<ComponentName>()
            val saved = updateRules(packageName) { current ->
                var rules = current
                groupedMap[packageName]?.forEach componentLoop@{ component ->
                    val type = componentTypeResolver.getComponentType(packageName, component.className)
                    if (type == PROVIDER) {
                        Timber.d("Cannot remove IFW rule for $component")
                        return@componentLoop
                    }
                    val ifwType = type.toIfwComponentType() ?: return@componentLoop
                    val formattedName = component.flattenToString()
                    rules = rules.removeComponentFilter(ifwType, formattedName)
                    removed += component
                }
                rules
            }
            // Report only saved changes, and outside the lock so callbacks cannot deadlock on it.
            if (saved) removed.forEach { callback(it) }
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        // Unreadable rules report the component as not blocked, as before. Callers have no
        // error path for this read, and writes refuse to touch an unreadable file.
        val cached = cache[packageName]
        if (cached == null && packageName in packagesReadWithoutRoot) return true
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
            packagesReadWithoutRoot += packageName
            return StoredRules.Unreadable(RootUnavailableException())
        }
        packagesReadWithoutRoot.clear()
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
     * Returns false without writing when the current rules cannot be read.
     */
    private suspend fun updateRules(packageName: String, transform: suspend (IfwRules) -> IfwRules): Boolean = withPackageLock(packageName) {
        val current = loadRulesLocked(packageName).rulesOrNull()
        if (current == null) {
            Timber.w("Leave IFW rules for $packageName untouched because they cannot be read")
            return@withPackageLock false
        }
        saveRulesLocked(packageName, transform(current))
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
 * Returns a new [IfwRules] instance with the filter removed.
 */
private fun IfwRules.removeComponentFilter(
    componentType: IfwComponentType,
    name: String,
): IfwRules {
    val filter = IfwFilter.ComponentFilter(name)
    val existingRule = rulesFor(componentType).firstOrNull() ?: return this
    val updatedFilters = existingRule.filters - filter
    return if (updatedFilters.isEmpty()) {
        IfwRules(rules.filter { it !== existingRule })
    } else {
        val updatedRule = existingRule.copy(filters = updatedFilters)
        IfwRules(rules.map { if (it === existingRule) updatedRule else it })
    }
}
