// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.wake.api.navigation.WakeAppNavKey
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WakeAppRouteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun givenDetailKeptInComposition_whenOpeningPeerAndGoingBack_thenExpiresPreviousUndo() {
        val overview = WakeOverviewNavKey
        val app = WakeAppNavKey(DETAIL_PACKAGE)
        val peer = WakeAppNavKey("example.peer")
        val navigator = Navigator(
            NavigationState(overview, NavBackStack(overview), mapOf(overview to NavBackStack<NavKey>(overview, app))),
        )
        val viewModel = mock<WakeAppViewModel> {
            on { uiState } doReturn MutableStateFlow(WakeAppUiState(WakeAppIdentity(DETAIL_PACKAGE)))
        }
        compose.setContent {
            BlockerTheme {
                WakeAppRoute(
                    viewModel = viewModel,
                    active = navigator.isCurrent(app),
                    onBack = navigator::goBack,
                    onPeerClick = {},
                    onComponents = {},
                )
            }
        }

        compose.runOnIdle { navigator.navigate(peer) }
        compose.waitForIdle()
        verify(viewModel).leaveScreen()

        compose.runOnIdle { navigator.goBack() }
        compose.waitForIdle()
        verify(viewModel).leaveScreen()
    }
}
