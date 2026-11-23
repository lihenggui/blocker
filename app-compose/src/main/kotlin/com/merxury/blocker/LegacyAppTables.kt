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

package com.merxury.blocker

import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.flow.takeWhile
import timber.log.Timber
import javax.inject.Inject

/**
 * Fills the old app and component tables, which the screens not yet moved to the device
 * repositories read: app detail, search, the rule screens and rule import and export. The app list
 * filled them before it moved; remove this once nothing reads them.
 */
class LegacyAppTables @Inject constructor(
    private val appRepository: AppRepository,
    private val initializeDatabase: InitializeDatabaseUseCase,
) {
    /** Reads the installed apps into the old table, then scans their components once per install. */
    suspend fun fill() {
        appRepository.updateApplicationList().collect { result ->
            if (result is Result.Error) Timber.e(result.exception, "Cannot fill the old app table")
        }
        // The scan keeps watching the app table once done, so stop at its end.
        initializeDatabase().takeWhile { it is InitializeState.Initializing }.collect {}
    }
}
