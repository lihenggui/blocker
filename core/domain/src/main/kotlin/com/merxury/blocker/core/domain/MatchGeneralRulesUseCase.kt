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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

data class GeneralRuleMatchResult(
    val matchedAppCounts: Map<Int, Int>,
    val components: List<ComponentInfo>,
)

/** Shares one bounded, in-memory match result between rule counts and bulk control. */
@Singleton
class MatchGeneralRulesUseCase @Inject constructor(
    private val componentRepository: ComponentRepository,
    private val appRepository: AppRepository,
    private val userDataRepository: UserDataRepository,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cachedInput: MatchInput? = null
    private var cachedMatches: Matches? = null

    operator fun invoke(rules: List<GeneralRule>): Flow<GeneralRuleMatchResult> = flow {
        val started = TimeSource.Monotonic.markNow()
        val showSystemApps = userDataRepository.userData.first().showSystemApps
        val hiddenPackages = if (showSystemApps) {
            emptySet()
        } else {
            appRepository.getApplicationList().first().filter { it.isSystem }.map { it.packageName }.toSet()
        }
        val components = componentRepository.getAllComponents().first()
            .distinctBy { ComponentKey(it.packageName, it.name) }
        val input = MatchInput(
            rules.map { RuleKeywords(it.id, it.searchKeyword.toList()) },
            components.map { ComponentKey(it.packageName, it.name) },
            hiddenPackages,
        )
        val snapshotTime = started.elapsedNow()
        var cacheHit = false
        val matches = mutex.withLock {
            if (input == cachedInput) {
                cacheHit = true
                checkNotNull(cachedMatches)
            } else {
                computeMatches(input).also {
                    currentCoroutineContext().ensureActive()
                    cachedInput = input
                    cachedMatches = it
                }
            }
        }
        // Component states are deliberately taken from the latest snapshot, never from the cache.
        val result = GeneralRuleMatchResult(
            matchedAppCounts = matches.appCounts,
            components = components.filter { ComponentKey(it.packageName, it.name) in matches.components },
        )
        Timber.d("Matched ${rules.size} rules against ${components.size} components: snapshot=$snapshotTime, total=${started.elapsedNow()}, cacheHit=$cacheHit")
        emit(result)
    }.flowOn(cpuDispatcher)

    private suspend fun computeMatches(input: MatchInput): Matches {
        val packagesByRule = input.rules.associate { it.id to mutableSetOf<String>() }
        val rulesByKeyword = mutableMapOf<String, MutableSet<Int>>()
        input.rules.forEach { rule ->
            rule.keywords.forEach { keyword -> rulesByKeyword.getOrPut(keyword) { mutableSetOf() }.add(rule.id) }
        }
        val patterns = rulesByKeyword.map { (keyword, ids) -> SqlLikePattern(keyword) to ids }
        val matchedComponents = mutableSetOf<ComponentKey>()
        for (component in input.components) {
            currentCoroutineContext().ensureActive()
            if (component.packageName in input.hiddenPackages) continue
            val name = component.name.substringBefore('\u0000').foldSqlLikeCase()
            for ((pattern, ids) in patterns) {
                if (pattern.matchesFolded(name)) {
                    matchedComponents.add(component)
                    ids.forEach { packagesByRule.getValue(it).add(component.packageName) }
                }
            }
        }
        return Matches(packagesByRule.mapValues { it.value.size }, matchedComponents)
    }

    private data class ComponentKey(val packageName: String, val name: String)
    private data class RuleKeywords(val id: Int, val keywords: List<String>)
    private data class MatchInput(
        val rules: List<RuleKeywords>,
        val components: List<ComponentKey>,
        val hiddenPackages: Set<String>,
    )
    private data class Matches(val appCounts: Map<Int, Int>, val components: Set<ComponentKey>)
}
