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

import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Whether su has started a root process for Blocker before, kept across process starts.
 *
 * An app cannot ask the root manager whether su will answer without a prompt. Root managers keep
 * an answer per app and give a stored "allow" without asking, so the last su start succeeding is
 * the evidence [RootTransport] needs before starting su where no prompt may appear.
 */
internal interface RootGrantMemory {
    /** Whether a root process was started last time su was asked. False when that cannot be read. */
    suspend fun isRemembered(): Boolean

    /** Records whether su gave a root process. A failed write is logged, not thrown. */
    suspend fun remember(granted: Boolean)
}

/** [RootGrantMemory] in the app properties DataStore. */
internal class DataStoreRootGrantMemory @Inject constructor(
    private val properties: BlockerAppPropertiesDataStore,
) : RootGrantMemory {

    override suspend fun isRemembered(): Boolean = try {
        properties.rootGrantRemembered.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Cannot read whether root was granted before")
        false
    }

    override suspend fun remember(granted: Boolean) {
        try {
            properties.setRootGrantRemembered(granted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot remember that root was granted: $granted")
        }
    }
}
