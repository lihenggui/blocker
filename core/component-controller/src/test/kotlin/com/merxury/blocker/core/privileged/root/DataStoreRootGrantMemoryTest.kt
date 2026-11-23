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

package com.merxury.blocker.core.privileged.root

import androidx.datastore.core.DataStore
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class DataStoreRootGrantMemoryTest {

    private val store = InMemoryDataStore(AppProperties.getDefaultInstance())

    @Test
    fun givenNothingStored_whenRead_thenNotRemembered() = runTest {
        assertThat(DataStoreRootGrantMemory(BlockerAppPropertiesDataStore(store)).isRemembered()).isFalse()
    }

    @Test
    fun givenRemembered_whenReadByNextProcess_thenRemembered() = runTest {
        DataStoreRootGrantMemory(BlockerAppPropertiesDataStore(store)).remember(true)

        assertThat(DataStoreRootGrantMemory(BlockerAppPropertiesDataStore(store)).isRemembered()).isTrue()
        assertThat(store.data.value.rootGrantRemembered).isTrue()
    }

    @Test
    fun givenRemembered_whenForgotten_thenNotRemembered() = runTest {
        val memory = DataStoreRootGrantMemory(BlockerAppPropertiesDataStore(store))
        memory.remember(true)

        memory.remember(false)

        assertThat(memory.isRemembered()).isFalse()
    }

    @Test
    fun givenStoreFails_whenReadAndWrite_thenNotRememberedAndNothingThrown() = runTest {
        val memory = DataStoreRootGrantMemory(BlockerAppPropertiesDataStore(BrokenDataStore()))

        memory.remember(true)

        assertThat(memory.isRemembered()).isFalse()
    }
}

private class BrokenDataStore : DataStore<AppProperties> {
    override val data: Flow<AppProperties> = flow { throw IOException("Cannot read") }

    override suspend fun updateData(transform: suspend (t: AppProperties) -> AppProperties): AppProperties = throw IOException("Cannot write")
}
