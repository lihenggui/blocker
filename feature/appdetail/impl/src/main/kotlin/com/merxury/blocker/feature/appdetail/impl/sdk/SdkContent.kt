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

package com.merxury.blocker.feature.appdetail.impl.sdk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.ui.collapseList.CollapsibleHeader
import com.merxury.blocker.core.ui.component.NoComponentScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.appdetail.impl.MatchedRule
import com.merxury.blocker.feature.appdetail.impl.MatchedRulesUiState
import com.merxury.blocker.feature.appdetail.impl.ModeBanner
import com.merxury.blocker.feature.appdetail.impl.SwitchState
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowItem
import com.merxury.blocker.feature.appdetail.impl.ui.ModeBanners
import com.merxury.blocker.core.ui.R.string as uiString

/**
 * The general rules that match components of the app, each expandable to its components. The
 * block and enable actions of a rule go through the same control path as the switches.
 *
 * @param batchRunning whether a batch runs for the app, which disables the actions of every rule
 */
@Composable
internal fun SdkContent(
    data: MatchedRulesUiState,
    inFlight: Set<ComponentId>,
    banners: List<ModeBanner>,
    actions: ComponentRowActions,
    modifier: Modifier = Modifier,
    batchRunning: Boolean = false,
    navigateToRuleDetail: (Int) -> Unit = {},
    onBlockAll: (MatchedRule) -> Unit = {},
    onEnableAll: (MatchedRule) -> Unit = {},
    onGrantAccess: () -> Unit = {},
) {
    when (data) {
        MatchedRulesUiState.Loading -> LoadingScreen()

        is MatchedRulesUiState.Loaded -> if (data.rules.isEmpty()) {
            NoComponentScreen()
        } else {
            MatchedRuleList(
                rules = data.rules,
                inFlight = inFlight,
                batchRunning = batchRunning,
                banners = banners,
                actions = actions,
                navigateToRuleDetail = navigateToRuleDetail,
                onBlockAll = onBlockAll,
                onEnableAll = onEnableAll,
                onGrantAccess = onGrantAccess,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MatchedRuleList(
    rules: List<MatchedRule>,
    inFlight: Set<ComponentId>,
    batchRunning: Boolean,
    banners: List<ModeBanner>,
    actions: ComponentRowActions,
    navigateToRuleDetail: (Int) -> Unit,
    onBlockAll: (MatchedRule) -> Unit,
    onEnableAll: (MatchedRule) -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.testTag("app:sdkList"), state = rememberLazyListState()) {
            if (banners.isNotEmpty()) {
                item(key = "banners") { ModeBanners(banners = banners, onGrantAccess = onGrantAccess) }
            }
            rules.forEach { rule ->
                val isExpanded = rule.id in expanded
                item(key = rule.id) {
                    CollapsibleHeader(
                        modifier = Modifier.animateItem(),
                        title = rule.title,
                        icon = rule.icon,
                        matchedCount = rule.rows.size,
                        blockedCount = rule.rows.count { it.switch == SwitchState.OFF },
                        enabledCount = rule.rows.count { it.switch == SwitchState.ON },
                        navigationMenuItemDesc = uiString.core_ui_open_rule_detail,
                        navigation = { navigateToRuleDetail(rule.id) },
                        onBlockAllClick = { onBlockAll(rule) },
                        onEnableAllClick = { onEnableAll(rule) },
                        batchActionsEnabled = !batchRunning,
                        expanded = isExpanded,
                        onCardArrowClick = { expanded = if (isExpanded) expanded - rule.id else expanded + rule.id },
                    )
                    HorizontalDivider()
                }
                if (isExpanded) {
                    items(items = rule.rows, key = { "${rule.id}/${it.id.className}" }) { row ->
                        ComponentRowItem(row = row, inFlight = row.id in inFlight, actions = actions, modifier = Modifier.animateItem())
                    }
                    item(key = "${rule.id}/divider") { HorizontalDivider() }
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
    }
}
