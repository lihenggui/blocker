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

package com.merxury.blocker.core.data.respository.generalrule

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.database.generalrule.fromExternalModel
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber

class OfflineFirstGeneralRuleRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val generalRuleDao: GeneralRuleDao = mock()
    private val dataSource: GeneralRuleDataSource = mock()
    private val repository = OfflineFirstGeneralRuleRepository(
        generalRuleDao = generalRuleDao,
        dataSource = dataSource,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )
    private val rule = GeneralRule(
        id = 1,
        name = "Rule",
        networkSignature = listOf("api.example.com"),
        website = "https://example.com",
    )
    private val cachedRule = GeneralRuleEntity(
        id = rule.id,
        name = rule.name,
        networkSignature = rule.networkSignature,
        website = rule.website,
        matchedAppCount = 7,
    )

    @Before
    fun setup() {
        whenever(generalRuleDao.getGeneralRuleEntities()).thenReturn(flowOf(listOf(cachedRule)))
    }

    @Test
    fun givenDuplicateRuleIds_whenUpdateGeneralRule_thenKeepsFirstOccurrenceAndAllUniqueRules() = runTest {
        val secondRule = GeneralRule(id = 2, name = "Second rule")
        val thirdRule = GeneralRule(id = 3, name = "Third rule")
        val duplicates = listOf(
            rule.copy(name = "Duplicate rule"),
            secondRule.copy(name = "Duplicate second rule"),
            rule.copy(name = "Another duplicate rule"),
        )
        whenever(generalRuleDao.getGeneralRuleEntities()).thenReturn(flowOf(emptyList()))
        whenever(dataSource.getGeneralRules()).thenReturn(
            flowOf(listOf(rule, secondRule, duplicates[0], thirdRule, duplicates[1], duplicates[2])),
        )
        val warnings = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority == Log.WARN) warnings.add(message)
            }
        }
        Timber.plant(tree)
        try {
            val results = repository.updateGeneralRule().toList()

            assertThat(results).containsExactly(Result.Loading, Result.Success(Unit)).inOrder()
            val entities = argumentCaptor<GeneralRuleEntity>()
            verify(generalRuleDao, times(3)).upsertGeneralRule(entities.capture())
            assertThat(entities.allValues).containsExactly(
                rule.fromExternalModel(),
                secondRule.fromExternalModel(),
                thirdRule.fromExternalModel(),
            ).inOrder()
            assertThat(warnings).hasSize(3)
            duplicates.forEachIndexed { index, duplicate ->
                val first = if (duplicate.id == rule.id) rule else secondRule
                assertThat(warnings[index]).contains(duplicate.id.toString())
                assertThat(warnings[index]).contains(first.name)
                assertThat(warnings[index]).contains(duplicate.name)
            }
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun givenChangedNetworkSignature_whenUpdateGeneralRule_thenRefreshesCacheAndPreservesMatchedAppCount() = runTest {
        val updated = rule.copy(networkSignature = listOf("new.example.com"))
        whenever(dataSource.getGeneralRules()).thenReturn(flowOf(listOf(updated)))

        repository.updateGeneralRule().toList()

        verify(generalRuleDao).upsertGeneralRule(
            cachedRule.copy(networkSignature = updated.networkSignature),
        )
    }

    @Test
    fun givenChangedWebsite_whenUpdateGeneralRule_thenRefreshesCacheAndPreservesMatchedAppCount() = runTest {
        val updated = rule.copy(website = "https://new.example.com")
        whenever(dataSource.getGeneralRules()).thenReturn(flowOf(listOf(updated)))

        repository.updateGeneralRule().toList()

        verify(generalRuleDao).upsertGeneralRule(cachedRule.copy(website = updated.website))
    }

    @Test
    fun givenClearedNetworkSignatureAndWebsite_whenUpdateGeneralRule_thenRefreshesCache() = runTest {
        whenever(dataSource.getGeneralRules()).thenReturn(
            flowOf(listOf(rule.copy(networkSignature = emptyList(), website = null))),
        )

        repository.updateGeneralRule().toList()

        verify(generalRuleDao).upsertGeneralRule(cachedRule.copy(networkSignature = emptyList(), website = null))
    }

    @Test
    fun givenUnchangedRuleData_whenUpdateGeneralRule_thenSkipsSavingDespiteDifferentMatchedAppCount() = runTest {
        whenever(dataSource.getGeneralRules()).thenReturn(flowOf(listOf(rule)))

        val results = repository.updateGeneralRule().toList()

        assertThat(results).containsExactly(Result.Loading, Result.Success(Unit)).inOrder()
        verify(generalRuleDao, never()).upsertGeneralRule(any())
        verify(generalRuleDao, never()).delete(any())
    }
}
