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

package com.merxury.core.ifw

import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Collectors only refresh, so they keep up; a slow one makes writers wait instead of missing one.
private const val CHANGE_BUFFER = 64

/**
 * [IfwStore] over an [IfwFileSystem] that throws [IfwAccessException] when the directory cannot
 * be reached; see [IfwStore] for the rules it applies.
 *
 * Public so tests of other modules can run the real rule semantics over an in-memory file
 * system; the app gets its one instance from Hilt.
 */
class IntentFirewallStore(
    private val fileSystem: IfwFileSystem,
    private val serializer: IfwXmlSerializer,
    private val deserializer: IfwXmlDeserializer,
) : IfwStore {

    /** What a package's rule file held when it was read, and whether there was one. */
    private data class StoredRules(val rules: IfwRules, val exists: Boolean)

    // Every file access for a package runs under its lock, so a read-modify-write cannot
    // interleave with another write, and a read cannot cache what a write is replacing.
    private val locks = ConcurrentHashMap<String, Mutex>()

    // What blocks() answers from. Only well-formed files and missing ones are kept.
    private val cache = ConcurrentHashMap<String, IfwRules>()

    // Bumped by clearCache(), so a read that started before it is not cached.
    private val cacheGeneration = AtomicLong()

    private val mutableChanges = MutableSharedFlow<String>(extraBufferCapacity = CHANGE_BUFFER)
    override val changes: SharedFlow<String> = mutableChanges.asSharedFlow()

    override suspend fun blocks(packageName: String, components: Collection<ComponentDeclaration>, refresh: Boolean): Map<ComponentId, IfwBlock> {
        requireComponentsOf(packageName, components)
        if (components.none { it.type.isFilteredByIntentFirewall }) return components.associate { it.id to IfwBlock.NOT_BLOCKED }
        val rules = cache[packageName]?.takeUnless { refresh } ?: withLock(packageName) {
            if (refresh) cache.remove(packageName)
            cache[packageName] ?: when (val read = read(packageName, connect = false)) {
                is IfwResult.Success -> read.value.rules
                is IfwResult.Failure -> null
            }
        }
        return components.associate { component ->
            component.id to when {
                !component.type.isFilteredByIntentFirewall -> IfwBlock.NOT_BLOCKED
                rules == null -> IfwBlock.UNKNOWN
                else -> rules.blockOf(component)
            }
        }
    }

    override suspend fun setBlocked(
        packageName: String,
        changes: Map<ComponentDeclaration, Boolean>,
    ): IfwResult<Map<ComponentId, IfwBlock>> {
        requireComponentsOf(packageName, changes.keys)
        val providers = changes.keys.filterNot { it.type.isFilteredByIntentFirewall }.associate { it.id to IfwBlock.NOT_BLOCKED }
        val filtered = changes.filterKeys { it.type.isFilteredByIntentFirewall }
        if (filtered.isEmpty()) return IfwResult.Success(providers)
        Timber.i("Set IFW block of ${filtered.size} components of $packageName")
        val result = modify(packageName) { current ->
            filtered.entries.fold(current) { rules, (component, blocked) -> rules.withBlocked(component, blocked) }
        }
        return when (result) {
            is IfwResult.Success -> IfwResult.Success(providers + filtered.keys.associate { it.id to result.value.blockOf(it) })
            is IfwResult.Failure -> result
        }
    }

    override suspend fun rules(name: String): IfwResult<IfwRules> {
        requireRuleFileName(name)
        return when (val read = withLock(name) { read(name, connect = true) }) {
            is IfwResult.Success -> IfwResult.Success(read.value.rules)
            is IfwResult.Failure -> read
        }
    }

    override suspend fun update(name: String, transform: (IfwRules) -> IfwRules): IfwResult<IfwRules> {
        requireRuleFileName(name)
        return modify(name, transform)
    }

    override suspend fun allRules(): IfwResult<Map<String, IfwResult<IfwRules>>> {
        val names = when (val listed = ruleFileNames()) {
            is IfwResult.Success -> listed.value
            is IfwResult.Failure -> return listed
        }
        val all = buildMap {
            names.forEach { name ->
                when (val read = withLock(name) { read(name, connect = true) }) {
                    // Deleted since it was listed.
                    is IfwResult.Success -> if (read.value.exists) put(name, IfwResult.Success(read.value.rules))

                    is IfwResult.Failure -> put(name, read)
                }
            }
        }
        return IfwResult.Success(all)
    }

    override suspend fun packagesWithRuleFiles(): IfwResult<List<String>> = when (val listed = ruleFileNames()) {
        is IfwResult.Success -> IfwResult.Success(listed.value.filter(::isPackageRuleFileName))
        is IfwResult.Failure -> listed
    }

    override suspend fun ruleFileNames(): IfwResult<List<String>> = try {
        IfwResult.Success(fileSystem.listRuleFiles().filter(::isRuleFileName))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IfwAccessException) {
        Timber.w(e, "Cannot list IFW rule files")
        IfwResult.Failure(IfwFailure.NoAccess)
    } catch (e: Exception) {
        // The directory was reached but not listed; for the caller it is as unreachable.
        Timber.e(e, "Cannot list IFW rule files")
        IfwResult.Failure(IfwFailure.NoAccess)
    }

    override suspend fun clear(name: String): IfwResult<Boolean> {
        requireRuleFileName(name)
        // Runs to its end once started, as a save does, so the cache and changes match the file.
        return withLock(name) {
            withContext(NonCancellable) {
                val removed = changeFile(name) { fileSystem.deleteRules(name) }
                if (removed is IfwResult.Success) {
                    cache.remove(name)
                    if (removed.value) {
                        Timber.i("Cleared the IFW rules in $name.xml")
                        mutableChanges.emit(name)
                    }
                }
                removed
            }
        }
    }

    override suspend fun ruleFileText(name: String): IfwResult<String?> {
        requireRuleFileName(name)
        return withLock(name) { readText(name) }
    }

    override suspend fun restoreRuleFile(name: String, content: String): IfwResult<Boolean> {
        requireRuleFileName(name)
        return withLock(name) {
            when (val existing = readText(name)) {
                is IfwResult.Failure -> existing

                is IfwResult.Success -> if (existing.value != null) {
                    IfwResult.Success(false)
                } else {
                    // Runs to its end once started, as a save does.
                    when (val written = withContext(NonCancellable) { changeFile(name) { fileSystem.writeRules(name, content) } }) {
                        is IfwResult.Success -> {
                            Timber.i("Restored the IFW rule file $name.xml")
                            // The cache may hold "no file" for it from an earlier read.
                            cache.remove(name)
                            mutableChanges.emit(name)
                            IfwResult.Success(true)
                        }

                        is IfwResult.Failure -> written
                    }
                }
            }
        }
    }

    /** The text of `<name>.xml` as stored, or null when there is none; call it under the name's lock. */
    private suspend fun readText(name: String): IfwResult<String?> = try {
        IfwResult.Success(fileSystem.readRules(name))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IfwAccessException) {
        Timber.d("Cannot reach the IFW rule file $name: ${e.message}")
        IfwResult.Failure(IfwFailure.NoAccess)
    } catch (e: Exception) {
        Timber.e(e, "Cannot read the IFW rule file $name")
        IfwResult.Failure(IfwFailure.Unreadable(name))
    }

    override fun clearCache() {
        Timber.d("Clear the IFW rule cache")
        cacheGeneration.incrementAndGet()
        cache.clear()
    }

    /**
     * Reads the current rules, applies [transform] and saves the result, all under the package's
     * lock. Writes nothing when the rules cannot be read or do not change. A write that has
     * started runs to its end even if the caller is cancelled, so the cache and [changes] match
     * the file; the caller still sees the cancellation afterwards.
     */
    private suspend fun modify(packageName: String, transform: (IfwRules) -> IfwRules): IfwResult<IfwRules> = withLock(packageName) {
        val current = when (val read = read(packageName, connect = true)) {
            is IfwResult.Success -> read.value

            is IfwResult.Failure -> {
                Timber.w("Leave IFW rules of $packageName untouched: ${read.reason}")
                return@withLock read
            }
        }
        val updated = transform(current.rules)
        when {
            updated == current.rules -> IfwResult.Success(updated)
            updated.isEmpty() && !current.exists -> IfwResult.Success(IfwRules.empty())
            else -> withContext(NonCancellable) { save(packageName, updated) }
        }
    }

    private suspend fun save(packageName: String, rules: IfwRules): IfwResult<IfwRules> {
        val saved = if (rules.isEmpty()) IfwRules.empty() else rules
        val written = changeFile(packageName) {
            if (saved.isEmpty()) {
                fileSystem.deleteRules(packageName)
            } else {
                fileSystem.writeRules(packageName, serializer.serialize(saved))
            }
        }
        if (written is IfwResult.Failure) return written
        cache[packageName] = saved
        mutableChanges.emit(packageName)
        return IfwResult.Success(saved)
    }

    /**
     * Writes or deletes the package's rule file with [change]; call it under the package's lock.
     * A failure drops what [blocks] remembers of the package and comes back as
     * [IfwFailure.NoAccess] or [IfwFailure.WriteFailed].
     */
    private suspend fun <T> changeFile(packageName: String, change: suspend () -> T): IfwResult<T> = try {
        IfwResult.Success(change())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IfwAccessException) {
        cache.remove(packageName)
        Timber.w(e, "Cannot change the IFW rule file of $packageName")
        IfwResult.Failure(IfwFailure.NoAccess)
    } catch (e: Exception) {
        // The old file is still there, or the new one if only the answer was lost.
        cache.remove(packageName)
        Timber.e(e, "Cannot change the IFW rule file of $packageName")
        IfwResult.Failure(IfwFailure.WriteFailed(packageName, e.message))
    }

    /**
     * Reads and parses the package's rule file; call it under the package's lock. Remembers what
     * it found for [blocks] unless [clearCache] ran meanwhile. With [connect], acquires the
     * privileged channel when needed.
     */
    private suspend fun read(packageName: String, connect: Boolean): IfwResult<StoredRules> {
        val generation = cacheGeneration.get()
        val content = try {
            if (connect) fileSystem.readRules(packageName) else fileSystem.readRulesIfConnected(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IfwAccessException) {
            // Expected without a channel; the caller reports it.
            Timber.d("Cannot reach the IFW rules of $packageName: ${e.message}")
            return IfwResult.Failure(IfwFailure.NoAccess)
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the IFW rules of $packageName")
            return IfwResult.Failure(IfwFailure.Unreadable(packageName))
        }
        val rules = if (content == null) {
            IfwRules.empty()
        } else {
            try {
                deserializer.deserialize(content)
            } catch (e: Exception) {
                Timber.e(e, "Cannot parse the IFW rules of $packageName")
                return IfwResult.Failure(IfwFailure.Unreadable(packageName))
            }
        }
        if (cacheGeneration.get() == generation) {
            cache[packageName] = rules
            // clearCache() may have run between the check and the put.
            if (cacheGeneration.get() != generation) cache.remove(packageName)
        }
        return IfwResult.Success(StoredRules(rules, exists = content != null))
    }

    private suspend inline fun <T> withLock(packageName: String, action: () -> T): T = locks.computeIfAbsent(packageName) { Mutex() }.withLock { action() }

    private fun requireComponentsOf(packageName: String, components: Collection<ComponentDeclaration>) {
        requirePackageName(packageName)
        components.forEach { require(it.id.packageName == packageName) { "${it.id} is not a component of $packageName" } }
    }

    private fun requirePackageName(packageName: String) {
        require(isPackageRuleFileName(packageName)) { "Not a package name for a rule file: $packageName" }
    }

    private fun requireRuleFileName(name: String) {
        require(isRuleFileName(name)) { "Not a rule file name: $name" }
    }
}
