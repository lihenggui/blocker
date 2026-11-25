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

package com.merxury.blocker.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BlockerAppPropertiesDataStore @Inject constructor(
    private val appProperties: DataStore<AppProperties>,
) {
    /**
     * Whether su last started a root process for Blocker, kept across process starts. Starting su
     * again shows no prompt while the root manager keeps that grant.
     */
    val rootGrantRemembered: Flow<Boolean> = appProperties.data.map { it.rootGrantRemembered }

    suspend fun setRootGrantRemembered(remembered: Boolean) {
        appProperties.updateData {
            it.copy {
                rootGrantRemembered = remembered
            }
        }
    }

    /**
     * Whether the record of components Blocker disabled through PackageManager has been seeded
     * from the component list earlier versions kept. Stays set once set.
     */
    val pmBlockRecordsSeeded: Flow<Boolean> = appProperties.data.map { it.pmBlockRecordsSeeded }

    suspend fun markPmBlockRecordsSeeded(rememberLegacyRootGrant: Boolean) {
        appProperties.updateData {
            it.copy {
                pmBlockRecordsSeeded = true
                if (rememberLegacyRootGrant && !hasRootGrantRemembered()) rootGrantRemembered = true
            }
        }
    }
}
