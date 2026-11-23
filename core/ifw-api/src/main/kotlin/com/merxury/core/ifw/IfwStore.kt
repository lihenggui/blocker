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
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.model.IfwRules
import kotlinx.coroutines.flow.SharedFlow

/**
 * The Intent Firewall rule file of each package, `/data/system/ifw/<package>.xml`. Rules apply to
 * every user on the device. Only a package's own file is read for its components; rules in other
 * files, such as the wake feature's, are not considered.
 *
 * ## What blocks a component
 * A rule *names* a component when it has a top-level `<component-filter>` for it. A rule is
 * *unconditional* when it has `block="true"` and every top-level filter element is a
 * `<component-filter>`. Its `log` attribute does not matter, and neither do its
 * `<intent-filter>`s: Android chooses a rule for an intent when the target matches one of its
 * component-filters *or* the intent matches one of its intent-filters, then applies the rule when
 * all its other filter elements match. A component of an activity, service or receiver is
 * [IfwBlock.BLOCKED] when an unconditional rule of its type names it, else
 * [IfwBlock.CONDITIONAL] when a rule with `block="true"` and further filter elements, such as a
 * sender filter, names it, else [IfwBlock.NOT_BLOCKED]. Providers are never filtered and always
 * [IfwBlock.NOT_BLOCKED]. A package without a rule file has nothing blocked.
 *
 * ## Writes
 * Blocking adds the component-filter to the first unconditional rule of the type that has no
 * intent-filter, or appends such a rule. Unblocking removes the component-filter from every
 * unconditional rule of the type and drops a rule left with neither component-filters nor
 * intent-filters. Neither touches a rule with further conditions, so an unblocked component can
 * stay [IfwBlock.CONDITIONAL]. Every change to a package runs as one read-modify-write under that
 * package's lock: a file that cannot be read is never written, a change that leaves the rules as
 * they are writes nothing, and a file is replaced atomically. A rewritten file keeps every rule
 * and filter Blocker models; comments, unknown attributes and unknown elements directly under
 * `<rules>` are dropped, and rules come out grouped by type. A file with an unknown filter
 * element cannot be read, so it is never rewritten.
 *
 * ## Cache
 * [blocks] answers from rules parsed earlier, so it may miss a change another writer made, such as
 * the older `IIntentFirewall`, until that package is written through this store or
 * [clearCache] runs. Every other read, and the read of every read-modify-write, goes to the file.
 */
interface IfwStore {
    /**
     * IFW state of each given component of one package. Never throws for expected failures: when
     * the file cannot be read or the rules cannot be reached, every filtered component is
     * [IfwBlock.UNKNOWN]. Never connects the privileged channel, so it never shows a prompt.
     *
     * @param components components of [packageName]
     */
    suspend fun blocks(packageName: String, components: Collection<ComponentDeclaration>): Map<ComponentId, IfwBlock>

    /**
     * Blocks (true) or unblocks (false) components of one package in one read-modify-write.
     * Returns the resulting [IfwBlock] of every component in [changes]; asking to block a
     * provider is not an error, it stays [IfwBlock.NOT_BLOCKED] and nothing is written for it.
     *
     * @param changes components of [packageName], each with whether to block it
     */
    suspend fun setBlocked(packageName: String, changes: Map<ComponentDeclaration, Boolean>): IfwResult<Map<ComponentId, IfwBlock>>

    /** Full rules for the editor; empty when the package has no rule file. */
    suspend fun rules(packageName: String): IfwResult<IfwRules>

    /**
     * The one read-modify-write path for editor saves. [transform] gets the current rules and
     * runs only when they could be read; rules for which [IfwRules.isEmpty] holds delete the
     * file. Returns the rules now in effect.
     */
    suspend fun update(packageName: String, transform: (IfwRules) -> IfwRules): IfwResult<IfwRules>

    /**
     * Packages that have a rule file, each with its rules or the reason they cannot be read.
     * Fails as a whole with [IfwFailure.NoAccess] when the directory cannot be listed.
     */
    suspend fun allRules(): IfwResult<Map<String, IfwResult<IfwRules>>>

    /** Drops the rules [blocks] remembers, so its next answers read the files again. */
    fun clearCache()

    /** Emits a package name after its rule file changed through this store. */
    val changes: SharedFlow<String>
}
