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

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.SqlLikePattern
import com.merxury.blocker.core.domain.foldSqlLikeCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.system.InstalledApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

/**
 * Matches the general rules against the components cached for the apps of one user, as the rule
 * list, a rule's detail and the search show them.
 *
 * A rule matches a component whose class name contains one of the rule's keywords the way SQL's
 * `LIKE '%keyword%'` does: ignoring case, with `%` and `_` as wildcards. Apps that are not cached
 * as installed for the user are left out, so are system apps while the user hides them, and so is
 * Blocker in the user it runs in: changing it from a rule could only break the app in use.
 *
 * The matches follow the cache: whenever it, the rules or the apps change they are matched again,
 * and each component carries the state the cache holds now. Packages whose declarations did not
 * change keep their earlier matches. Nothing is persisted.
 */
@Singleton
class GeneralRuleMatcher @Inject constructor(
    private val componentStatuses: ComponentStatusRepository,
    private val installedApps: InstalledAppRepository,
    private val generalRules: GeneralRuleRepository,
    private val userData: UserDataRepository,
    selectedUsers: SelectedUserRepository,
    @AppPackageName appPackageName: String,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val self = AppKey(appPackageName, selectedUsers.ownUser)
    private val shared = ConcurrentHashMap<UserId, Flow<GeneralRuleMatches>>()

    /**
     * What every general rule matches among the cached apps of [user]. The first value comes once
     * the cache has been matched, then a new one after each change. Collectors of the same user
     * share one matching.
     */
    fun matches(user: UserId): Flow<GeneralRuleMatches> = shared.computeIfAbsent(user) {
        matching(it).shareIn(scope, SharingStarted.WhileSubscribed(KEEP_MATCHING_MILLIS, replayExpirationMillis = 0), replay = 1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun matching(user: UserId): Flow<GeneralRuleMatches> = flow {
        val memo = MatchMemo()
        val input = combine(
            generalRules.getGeneralRules(),
            componentStatuses.allComponents(user),
            installedApps.apps(user),
            userData.userData.map { it.showSystemApps }.distinctUntilChanged(),
        ) { rules, components, apps, showSystemApps -> MatchInput(rules, components, apps.filter { it.isShown(showSystemApps) }) }
        // A newer cache replaces a matching still running; the packages it matched stay in the memo.
        emitAll(input.mapLatest { memo.match(it) })
    }.flowOn(cpuDispatcher)

    private fun InstalledApp.isShown(showSystemApps: Boolean): Boolean = key != self && (showSystemApps || !isSystem)

    private class MatchInput(
        val rules: List<GeneralRule>,
        val components: List<ComponentStatus>,
        val shownApps: List<InstalledApp>,
    )

    /** The matches of each package by its class names, valid while the rules' keywords stay the same. */
    private class MatchMemo {
        private var keywords: List<Pair<Int, List<String>>>? = null
        private var patterns: List<Pair<SqlLikePattern, List<Int>>> = emptyList()
        private val packages = HashMap<String, PackageMatch>()

        suspend fun match(input: MatchInput): GeneralRuleMatches {
            val started = TimeSource.Monotonic.markNow()
            val ruleKeywords = input.rules.map { it.id to it.searchKeyword }
            if (ruleKeywords != keywords) {
                keywords = ruleKeywords
                patterns = patternsOf(input.rules)
                packages.clear()
            }
            val shown = input.shownApps.mapTo(HashSet()) { it.key.packageName }
            val byRule = HashMap<Int, MutableList<ComponentStatus>>()
            var reused = 0
            val byPackage = input.components.groupBy { it.declaration.id.packageName }
            byPackage.forEach { (packageName, statuses) ->
                if (packageName !in shown) return@forEach
                currentCoroutineContext().ensureActive()
                val names = statuses.map { it.declaration.id.className }
                val known = packages[packageName]?.takeIf { it.classNames == names }
                val match = known ?: PackageMatch(names, names.map(::rulesOf)).also { packages[packageName] = it }
                if (known != null) reused++
                statuses.forEachIndexed { index, status ->
                    match.rules[index].forEach { byRule.getOrPut(it) { mutableListOf() } += status }
                }
            }
            packages.keys.retainAll(byPackage.keys)
            Timber.v("Matched ${input.rules.size} rules against ${input.components.size} components in ${started.elapsedNow()}, ${byPackage.size} packages, $reused unchanged")
            return GeneralRuleMatches(byRule)
        }

        private fun rulesOf(className: String): List<Int> {
            val folded = className.substringBefore('\u0000').foldSqlLikeCase()
            val matched = LinkedHashSet<Int>()
            patterns.forEach { (pattern, ids) -> if (pattern.matchesFolded(folded)) matched += ids }
            return matched.toList()
        }

        private fun patternsOf(rules: List<GeneralRule>): List<Pair<SqlLikePattern, List<Int>>> {
            val rulesByKeyword = LinkedHashMap<String, MutableSet<Int>>()
            rules.forEach { rule -> rule.searchKeyword.forEach { rulesByKeyword.getOrPut(it) { LinkedHashSet() } += rule.id } }
            return rulesByKeyword.map { (keyword, ids) -> SqlLikePattern(keyword) to ids.toList() }
        }
    }

    /** The rules that match each of [classNames], in the same order. */
    private class PackageMatch(val classNames: List<String>, val rules: List<List<Int>>)

    private companion object {
        /** How long a matching keeps running without collectors, as while a screen is recreated. */
        const val KEEP_MATCHING_MILLIS = 5_000L
    }
}

/**
 * What the general rules match among the cached components of one user's apps.
 *
 * @property byRule the matched components of each rule that matches any, by rule id, each list by
 * package and class name
 */
data class GeneralRuleMatches(private val byRule: Map<Int, List<ComponentStatus>>) {
    /** The components the rule with [ruleId] matches, by package and class name. */
    fun componentsOf(ruleId: Int): List<ComponentStatus> = byRule[ruleId].orEmpty()

    /** How many apps have components the rule with [ruleId] matches. */
    fun appCountOf(ruleId: Int): Int = componentsOf(ruleId).mapTo(HashSet()) { it.declaration.id.packageName }.size

    /** Every component a rule matches, each once. */
    val allComponents: List<ComponentStatus>
        get() = byRule.values.flatten().distinctBy { it.declaration.id }
}
