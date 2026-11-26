// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.api.navigation

import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import kotlinx.serialization.Serializable

@Serializable
object WakeOverviewNavKey : NavKey

@Serializable
data class WakeAppNavKey(val packageName: String) : NavKey

fun Navigator.navigateToWakeOverview() = navigate(WakeOverviewNavKey)

fun Navigator.navigateToWakeApp(packageName: String) = navigate(WakeAppNavKey(packageName))
