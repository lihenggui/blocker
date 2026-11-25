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

package com.merxury.blocker.feature.ruledetail.impl

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.component.ComponentRow
import com.merxury.blocker.core.ui.control.ActivityLaunchFailed
import com.merxury.blocker.core.ui.control.QueuedControlMessage
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType
import java.io.File
import kotlin.time.Instant

/**
 * What the detail screen of one general rule shows, for the user selected in the app list.
 *
 * @property ownUser the user Blocker runs in, for which an app opens without naming its user
 * @property inFlight the components whose change has not ended, including those a batch of
 * another screen changes; their switches ignore taps
 * @property batch the progress of the batch running for the rule, or null
 * @property messages outcomes to tell, oldest first; see [RuleDetailViewModel.onMessageShown]
 * @property actionFailure a launch of an activity that failed, to tell once; see
 * [RuleDetailViewModel.onActionFailureShown]
 */
data class RuleDetailUiState(
    val ownUser: UserId,
    val ruleInfo: RuleInfoUiState = RuleInfoUiState.Loading,
    val tabState: TabState<RuleDetailTabs> = TabState(items = listOf(RuleDetailTabs.Applicable, RuleDetailTabs.Description), selectedItem = RuleDetailTabs.Applicable),
    val sortType: RuleDetailSortType = RuleDetailSortType.NAME,
    val inFlight: Set<ComponentId> = emptySet(),
    val batch: ProcessingProgress? = null,
    val messages: List<QueuedControlMessage> = emptyList(),
    val actionFailure: ActivityLaunchFailed? = null,
) {
    /** The sort and batch actions on the matched apps, which the description tab does not offer. */
    val appBarUiState: AppBarUiState
        get() = AppBarUiState(actions = if (tabState.selectedItem == RuleDetailTabs.Description) emptyList() else listOf(AppBarAction.SORT, AppBarAction.MORE))
}

sealed interface RuleInfoUiState {
    data object Loading : RuleInfoUiState

    class Error(val error: UiMessage) : RuleInfoUiState

    /**
     * @property ruleInfo the rule, with its icon as a file path
     * @property seedIcon the icon to theme the screen after, while the user chose dynamic colours
     */
    data class Success(
        val ruleInfo: GeneralRule,
        val matchedAppsUiState: MatchedAppsUiState,
        val seedIcon: File? = null,
        val batchActionsEnabled: Boolean = true,
    ) : RuleInfoUiState
}

sealed interface MatchedAppsUiState {
    /** The apps of the user are not matched yet. */
    data object Loading : MatchedAppsUiState

    /** The apps with components the rule matches, in the order chosen. */
    data class Loaded(val apps: List<MatchedApp>) : MatchedAppsUiState
}

/**
 * An app with the components of it the rule matches.
 *
 * @property icon the app's icon, or null for the Android icon
 * @property firstInstallTime when it was first installed for its user, for sorting
 */
data class MatchedApp(
    val app: AppKey,
    val label: String,
    val icon: ApkIcon?,
    val rows: List<ComponentRow>,
    val firstInstallTime: Instant? = null,
)
