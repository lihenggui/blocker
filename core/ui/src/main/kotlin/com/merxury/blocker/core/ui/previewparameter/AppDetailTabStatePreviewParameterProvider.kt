/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.ui.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState

class AppDetailTabStatePreviewParameterProvider :
    PreviewParameterProvider<List<TabState<AppDetailTabs>>> {
    override val values: Sequence<List<TabState<AppDetailTabs>>> = sequenceOf(
        listOf(
            TabState(
                items = listOf(
                    Info,
                    Receiver,
                    Service,
                    Activity,
                    Provider,
                ),
                selectedItem = Info,
                itemCount = mapOf(
                    Info to 1,
                    Receiver to 2,
                    Service to 3,
                    Activity to 4,
                    Provider to 5,
                ),
            ),
            TabState(
                items = listOf(
                    Info,
                    Receiver,
                ),
                selectedItem = Receiver,
            ),
        ),
    )
}
