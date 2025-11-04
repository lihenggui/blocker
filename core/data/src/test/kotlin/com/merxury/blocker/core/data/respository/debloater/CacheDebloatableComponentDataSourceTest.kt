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

package com.merxury.blocker.core.data.respository.debloater

import com.merxury.blocker.core.database.debloater.DebloatableComponentDao
import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity
import com.merxury.blocker.core.model.ComponentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class CacheDebloatableComponentDataSourceTest {

    private val debloatableComponentDao: DebloatableComponentDao = mock()
    private lateinit var dataSource: CacheDebloatableComponentDataSource

    @Before
    fun setup() {
        dataSource = CacheDebloatableComponentDataSource(debloatableComponentDao)
    }

    @Test
    fun givenCachedEntities_whenGetDebloatableComponent_thenReturnDataFromDao() = runTest {
        val entities = listOf(
            DebloatableComponentEntity(
                packageName = "com.example.app1",
                componentName = "com.example.app1.ShareActivity",
                simpleName = "ShareActivity",
                displayName = "Share via App1",
                ifwBlocked = false,
                pmBlocked = false,
                exported = true,
                type = ComponentType.ACTIVITY,
            ),
            DebloatableComponentEntity(
                packageName = "com.example.app2",
                componentName = "com.example.app2.SendActivity",
                simpleName = "SendActivity",
                displayName = "Send with App2",
                ifwBlocked = true,
                pmBlocked = false,
                exported = true,
                type = ComponentType.ACTIVITY,
            ),
        )

        whenever(debloatableComponentDao.getAll()).doReturn(flowOf(entities))

        val result = dataSource.getDebloatableComponent().first()

        assertEquals(entities, result)
        verify(debloatableComponentDao).getAll()
    }

    @Test
    fun givenEmptyCache_whenGetDebloatableComponent_thenReturnEmptyList() = runTest {
        whenever(debloatableComponentDao.getAll()).doReturn(flowOf(emptyList()))

        val result = dataSource.getDebloatableComponent().first()

        assertEquals(emptyList(), result)
    }
}
