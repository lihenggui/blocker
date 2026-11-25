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

package com.merxury.blocker.core.rule.backup

import android.content.Context
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.util.DocumentTreeBackupStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class BlockerBackupSets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /** Returns alternatives to the target's own set. The import worker reports storage failures. */
    suspend fun alternatives(path: String, target: UserId): List<BackupSet> = withContext(ioDispatcher) {
        if (path.isEmpty()) return@withContext emptyList()
        val storage = DocumentTreeBackupStorage.open(context, path, ioDispatcher) ?: return@withContext emptyList()
        try {
            storage.alternativeBackupSets(target)
        } catch (e: BackupStorageException) {
            Timber.w(e, "Cannot find alternate Blocker backup sets")
            emptyList()
        }
    }
}
