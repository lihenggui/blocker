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

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * How often the cache is queried and matched while the apps of a user are read into it one after
 * another, as an indexing writes them: three writes per app, [WRITE_GAP_MILLIS] apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GeneralRuleMatcherLoadTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private var passes = 0
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (message.startsWith("Matched ")) passes++
        }
    }

    /**
     * Reads a user's components as Room does: every write to the cache signals the query, signals
     * that arrive while the reader is busy collapse into one, and each read is one query. As the
     * cache's DAO does, a result equal to the one before is not emitted again.
     */
    private class CountingStatuses(private val delegate: FakeComponentStatusRepository) : ComponentStatusRepository by delegate {
        val writes = MutableStateFlow(0)
        var queries = 0
        override fun allComponents(user: UserId): Flow<List<ComponentStatus>> = writes
            .map {
                queries++
                delegate.allComponents(user).first()
            }
            .distinctUntilChanged()
    }

    @Before
    fun plant() = Timber.plant(tree)

    @After
    fun uproot() = Timber.uproot(tree)

    @Test
    fun givenAnIndexingOfManyApps_whenMatching_thenTheCacheIsQueriedAndMatchedAFewTimesASecond() = runTest {
        val components = FakeComponentStatusRepository()
        val counting = CountingStatuses(components)
        val apps = FakeInstalledAppRepository()
        val rules = TestGeneralRuleRepository().apply { sendRuleList(List(RULES) { GeneralRule(id = it, name = "Rule $it", searchKeyword = listOf("com.tracker$it.")) }) }
        val matcher = GeneralRuleMatcher(
            componentStatuses = counting,
            installedApps = apps,
            generalRules = rules,
            userData = TestUserDataRepository().apply { sendUserData(defaultUserData) },
            selectedUsers = FakeSelectedUserRepository(OWNER),
            appPackageName = SELF_PACKAGE,
            cpuDispatcher = dispatcherRule.testDispatcher,
            scope = backgroundScope,
        )
        apps.sendApps(List(APPS) { installedApp("com.app$it") })
        var emissions = 0
        var latest: GeneralRuleMatches? = null
        backgroundScope.launch {
            matcher.matches(OWNER).collect {
                emissions++
                latest = it
            }
        }

        val written = mutableListOf<ComponentStatus>()
        repeat(APPS) { app ->
            val declarations = List(COMPONENTS_PER_APP) { status("com.app$app", "com.tracker${it % RULES}.C$it", ifw = IfwBlock.UNKNOWN) }
            // The app row, its declarations with their settings, then its firewall state.
            repeat(3) { step ->
                if (step == 1) written += declarations
                if (step == 2) written.replaceAll { if (it.declaration.id.packageName == "com.app$app") it.copy(state = it.state.copy(ifw = IfwBlock.NOT_BLOCKED)) else it }
                components.sendComponents(OWNER, written.toList())
                counting.writes.value++
                delay(WRITE_GAP_MILLIS)
            }
        }
        // Work in the background scope does not count as busy, so the time is moved on explicitly.
        advanceTimeBy(1_000)
        runCurrent()
        val writes = APPS * 3
        println("Indexing $APPS apps, $writes writes: ${counting.queries} queries, $passes match passes, $emissions emissions")

        // 9 seconds of writes read once every 300 ms; before the cache was read at most that often,
        // this was 300 queries, 201 match passes and 201 emissions.
        assertThat(counting.queries).isAtMost(35)
        assertThat(passes).isAtMost(35)
        assertThat(emissions).isAtMost(35)
        // The last write still arrives: every app counts, with the firewall state it read last.
        assertThat(latest!!.appCountOf(0)).isEqualTo(APPS)
        assertThat(latest!!.allComponents.none { it.state.ifw == IfwBlock.UNKNOWN }).isTrue()
    }

    private companion object {
        const val APPS = 100
        const val COMPONENTS_PER_APP = 40
        const val RULES = 20
        const val WRITE_GAP_MILLIS = 30L
    }
}
