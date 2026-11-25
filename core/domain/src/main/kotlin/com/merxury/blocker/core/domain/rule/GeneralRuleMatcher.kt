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

import android.icu.lang.UCharacter
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.readAtMostEvery
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.system.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.retryWhen
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
 * and each component carries the state the cache holds now. The cache is read at most once every
 * [CACHE_READ_PERIOD_MILLIS], as an indexing writes to it many times a second, and a matching
 * equal to the one before is not emitted. Packages whose declarations did not change keep their
 * earlier matches. Nothing is persisted.
 *
 * A failure to read the rules or the cache is logged and thrown to the collectors, so a screen can
 * show its error; the matching starts over after [RETRY_MILLIS] and recovers once reading works.
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
    private val shared = ConcurrentHashMap<UserId, Flow<Result<GeneralRuleMatches>>>()

    /**
     * What every general rule matches among the cached apps of [user]. The first value comes once
     * the cache has been matched, then a new one after each change. Collectors of the same user
     * share one matching. Throws what kept the rules or the cache from being read; collecting
     * again later gets the matching that started over.
     */
    fun matches(user: UserId): Flow<GeneralRuleMatches> = shared.computeIfAbsent(user) {
        matching(it, generalRules.getGeneralRules()).shareIn(scope, SharingStarted.WhileSubscribed(KEEP_MATCHING_MILLIS, replayExpirationMillis = 0), replay = 1)
    }.map { it.getOrThrow() }

    /**
     * The components the general rule [ruleId] matches among the cached apps of [user], in the
     * order of [GeneralRuleMatches.componentsOf]. Only that rule is matched, so it comes quicker
     * than [matches] the first time. Throws as [matches] does.
     */
    fun matches(user: UserId, ruleId: Int): Flow<List<ComponentStatus>> = matching(user, generalRules.getGeneralRule(ruleId).map(::listOf))
        .map { it.getOrThrow().componentsOf(ruleId) }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun matching(user: UserId, rules: Flow<List<GeneralRule>>): Flow<Result<GeneralRuleMatches>> = flow {
        val memo = MatchMemo()
        val input = combine(
            rules,
            componentStatuses.allComponents(user).readAtMostEvery(CACHE_READ_PERIOD_MILLIS),
            installedApps.apps(user),
            userData.userData.map { it.showSystemApps }.distinctUntilChanged(),
        ) { rules, components, apps, showSystemApps -> MatchInput(rules, components, apps.filter { it.isShown(showSystemApps) }) }
        // A newer cache replaces a matching still running; the packages it matched stay in the memo.
        emitAll(input.mapLatest { Result.success(memo.match(it)) })
    }
        .retryWhen { cause, _ ->
            if (cause is CancellationException) return@retryWhen false
            Timber.e(cause, "Cannot match the general rules for user ${user.value}")
            emit(Result.failure(cause))
            delay(RETRY_MILLIS)
            true
        }
        .distinctUntilChanged()
        .flowOn(cpuDispatcher)

    private fun InstalledApp.isShown(showSystemApps: Boolean): Boolean = key != self && (showSystemApps || !isSystem)

    private class MatchInput(
        val rules: List<GeneralRule>,
        val components: List<ComponentStatus>,
        val shownApps: List<InstalledApp>,
    )

    /** The matches of each package by its class names, valid while the rules' keywords stay the same. */
    private class MatchMemo {
        private var keywords: List<Pair<Int, List<String>>>? = null

        // Each distinct keyword with the rules that list it and where each lists it.
        private var foldedKeywords: Map<String, List<KeywordOf>> = emptyMap()
        private val packages = HashMap<String, PackageMatch>()

        suspend fun match(input: MatchInput): GeneralRuleMatches {
            val started = TimeSource.Monotonic.markNow()
            val ruleKeywords = input.rules.map { it.id to it.searchKeyword }
            if (ruleKeywords != keywords) {
                keywords = ruleKeywords
                foldedKeywords = foldKeywords(input.rules)
                packages.clear()
            }
            val shown = input.shownApps.mapTo(HashSet()) { it.key.packageName }
            val byRule = HashMap<Int, MutableList<Pair<ComponentStatus, Int>>>()
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
                    match.rules[index].forEach { (ruleId, keyword) -> byRule.getOrPut(ruleId) { mutableListOf() } += status to keyword }
                }
            }
            packages.keys.retainAll(byPackage.keys)
            Timber.v("Matched ${input.rules.size} rules against ${input.components.size} components in ${started.elapsedNow()}, ${byPackage.size} packages, $reused unchanged")
            return GeneralRuleMatches(byRule.mapValues { (_, matched) -> matched.sortedWith(OLD_SCREEN_ORDER).map { it.first } })
        }

        /** The rules that match [className], each with the position of its first keyword that matches. */
        private fun rulesOf(className: String): Map<Int, Int> {
            val folded = className.foldKeywordCase()
            val matched = LinkedHashMap<Int, Int>()
            foldedKeywords.forEach { (keyword, rules) ->
                if (folded.contains(keyword)) rules.forEach { matched.merge(it.ruleId, it.position, ::minOf) }
            }
            return matched
        }

        private fun foldKeywords(rules: List<GeneralRule>): Map<String, List<KeywordOf>> {
            val rulesByKeyword = LinkedHashMap<String, MutableList<KeywordOf>>()
            rules.forEach { rule ->
                rule.searchKeyword.forEachIndexed { position, keyword -> rulesByKeyword.getOrPut(keyword.foldKeywordCase()) { mutableListOf() } += KeywordOf(rule.id, position) }
            }
            return rulesByKeyword
        }
    }

    /** The rules that match each of [classNames], in the same order, see [MatchMemo.rulesOf]. */
    private class PackageMatch(val classNames: List<String>, val rules: List<Map<Int, Int>>)

    /** A rule that lists a keyword, at [position] among its keywords. */
    private data class KeywordOf(val ruleId: Int, val position: Int)

    internal companion object {
        /** How long a matching keeps running without collectors, as while a screen is recreated. */
        const val KEEP_MATCHING_MILLIS = 5_000L

        /** The shortest time between two reads of the cache, which an indexing writes to many times a second. */
        const val CACHE_READ_PERIOD_MILLIS = 300L

        /** How long a matching that failed waits before it starts over. */
        const val RETRY_MILLIS = 5_000L

        /** The order the old component table filled its rows in: one app's activities, services, receivers, then providers. */
        private val TYPE_ORDER = listOf(ComponentType.ACTIVITY, ComponentType.SERVICE, ComponentType.RECEIVER, ComponentType.PROVIDER)

        /**
         * The order the rule detail listed an app's matched components in before it read the cache:
         * it searched one keyword after another, so the components of the rule's first keyword came
         * first, each search in the order the old table held the rows, by type. The table held each
         * type in manifest order, which the cache does not keep; the class name stands in for it.
         */
        private val OLD_SCREEN_ORDER: Comparator<Pair<ComponentStatus, Int>> = compareBy<Pair<ComponentStatus, Int>>(
            { it.first.declaration.id.packageName },
            { it.second },
            { TYPE_ORDER.indexOf(it.first.declaration.type) },
            { it.first.declaration.id.className },
        )
    }
}

/**
 * What the general rules match among the cached components of one user's apps.
 *
 * @property byRule the matched components of each rule that matches any, by rule id, each list by
 * package and then in the order the rule detail lists an app's components: those the rule's first
 * keyword matches first, then each type in turn, then by class name
 */
data class GeneralRuleMatches(private val byRule: Map<Int, List<ComponentStatus>>) {
    /** The components the rule with [ruleId] matches, by package, each package's in the order of [byRule]. */
    fun componentsOf(ruleId: Int): List<ComponentStatus> = byRule[ruleId].orEmpty()

    /** How many apps have components the rule with [ruleId] matches. */
    fun appCountOf(ruleId: Int): Int = componentsOf(ruleId).mapTo(HashSet()) { it.declaration.id.packageName }.size

    /** Every component a rule matches, each once. */
    val allComponents: List<ComponentStatus>
        get() = byRule.values.flatten().distinctBy { it.declaration.id }
}

private fun String.foldKeywordCase(): String {
    if (all { it.code < 128 }) {
        if (none { it in 'A'..'Z' }) return this
        return buildString(length) {
            for (char in this@foldKeywordCase) append(if (char in 'A'..'Z') char + ('a' - 'A') else char)
        }
    }
    // Preserve the simple Unicode case folding used for literal keywords.
    return buildString(length) {
        var index = 0
        while (index < this@foldKeywordCase.length) {
            val codePoint = this@foldKeywordCase.codePointAt(index)
            appendCodePoint(UCharacter.foldCase(codePoint, true))
            index += Character.charCount(codePoint)
        }
    }
}
