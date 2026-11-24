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

package com.merxury.blocker.feature.search.impl.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.ui.applist.needsAccess
import com.merxury.blocker.core.ui.applist.text
import com.merxury.blocker.core.ui.control.text
import com.merxury.blocker.feature.search.api.R.plurals
import com.merxury.blocker.feature.search.api.R.string
import com.merxury.blocker.feature.search.impl.ComponentGroup
import com.merxury.blocker.feature.search.impl.SearchMessage
import com.merxury.blocker.feature.search.impl.SearchTab
import com.merxury.blocker.core.ui.R.string as uiString

/** What a snackbar says about [this] message. */
@Composable
internal fun SearchMessage.text(): String = when (this) {
    is SearchMessage.AppFailed -> failure.text()
    is SearchMessage.Control -> message.text()
}

/** Whether granting root or Shizuku access would help, which the message then offers. */
internal val SearchMessage.needsAccess: Boolean
    get() = when (this) {
        is SearchMessage.AppFailed -> failure.needsAccess
        is SearchMessage.Control -> message.needsAccess
    }

/** The title of [this] tab with its number of results. */
@Composable
internal fun SearchTab.title(count: Int): String = when (this) {
    SearchTab.APPS -> stringResource(uiString.core_ui_application_with_count, count)
    SearchTab.COMPONENTS -> stringResource(uiString.core_ui_component_with_count, count)
    SearchTab.RULES -> stringResource(uiString.core_ui_online_rule_with_count, count)
}

/** How many components of each type were found in [group], such as "2 Receivers / 1 Service". */
@Composable
internal fun countsOf(group: ComponentGroup): String = listOf(
    ComponentType.RECEIVER to plurals.feature_search_api_receiver_count,
    ComponentType.SERVICE to plurals.feature_search_api_service_count,
    ComponentType.ACTIVITY to plurals.feature_search_api_activity_count,
    ComponentType.PROVIDER to plurals.feature_search_api_content_provider_count,
).mapNotNull { (type, plural) ->
    val count = group.components.count { it.type == type }
    if (count == 0) null else pluralStringResource(plural, count, count)
}.joinToString(separator = stringResource(string.feature_search_api_delimiter))
