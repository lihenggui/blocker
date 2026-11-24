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

package com.merxury.blocker.feature.debloater.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdebloater.api.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebloaterPermissionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun givenPendingPermission_whenDeniedThenGranted_thenShowAndRemoveTheWarning() {
        val appScope = CoroutineScope(Dispatchers.Main.immediate)
        val fixture = DebloaterFixture(appScope, Dispatchers.Main.immediate)
        fixture.permission.value = null
        val snackbarHostState = SnackbarHostState()
        try {
            composeTestRule.setContent {
                BlockerTheme {
                    DebloaterScreen(snackbarHostState, navigateBack = {}, viewModel = fixture.viewModel)
                }
            }
            composeTestRule.runOnIdle {
                assertThat(snackbarHostState.currentSnackbarData).isNull()
                fixture.permission.value = PermissionStatus.NO_PERMISSION
            }
            val message = composeTestRule.activity.getString(R.string.feature_debloater_api_no_root_permission)
            composeTestRule.runOnIdle {
                val warning = snackbarHostState.currentSnackbarData?.visuals
                assertThat(warning?.message).isEqualTo(message)
                assertThat(warning?.duration).isEqualTo(SnackbarDuration.Long)
                fixture.permission.value = PermissionStatus.ROOT_USER
            }
            composeTestRule.runOnIdle {
                assertThat(snackbarHostState.currentSnackbarData).isNull()
            }
        } finally {
            fixture.viewModel.viewModelScope.cancel()
            appScope.cancel()
        }
    }
}
