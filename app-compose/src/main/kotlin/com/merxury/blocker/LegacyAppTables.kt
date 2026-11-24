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

import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.takeWhile
import timber.log.Timber
import javax.inject.Inject

/**
 * Fills the old app and component tables, which the screens not yet moved to the device
 * repositories read: the general rules and rule detail, the global firewall rules, the debloater,
 * the matching of general rules and rule import and export. The app list filled them before it
 * moved. Delete this class once the last screen that reads the old tables
 * has moved to the device repositories.
 */
class LegacyAppTables @Inject constructor(
    private val appRepository: AppRepository,
    private val initializeDatabase: InitializeDatabaseUseCase,
    private val installedApps: InstalledAppRepository,
    private val selectedUsers: SelectedUserRepository,
) {
    /**
     * Reads the installed apps into the old table and scans their components once per install.
     * Then reads the apps into the old table again whenever the cached apps of the user Blocker
     * runs in change, as a refresh or an action in the app list changes them, so an app installed
     * or removed while Blocker runs reaches the old screens too. The old table holds that user
     * only. A failure is logged and never reaches the app list. Returns only when cancelled.
     */
    suspend fun keepFilled() {
        fillAppTable()
        // The scan keeps watching the app table once done, so stop at its end.
        initializeDatabase().takeWhile { it is InitializeState.Initializing }.collect {}
        installedApps.apps(selectedUsers.ownUser)
            .distinctUntilChanged()
            // Each fill runs to its end: it saves the app rows before their components, and a
            // cancelled fill would leave components behind that the next fill no longer sees as
            // changed. Changes arriving meanwhile collapse into one more fill.
            .conflate()
            .collect { fillAppTable() }
    }

    private suspend fun fillAppTable() {
        appRepository.updateApplicationList().collect { result ->
            if (result is Result.Error) Timber.e(result.exception, "Cannot fill the old app table")
        }
    }
}
