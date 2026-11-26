// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.test

import com.merxury.blocker.core.data.wake.WakeRunRequester
import com.merxury.blocker.core.testing.util.TestWakeRunRequester
import com.merxury.blocker.sync.di.WakeRunModule
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [WakeRunModule::class])
interface TestWakeRunModule {
    @Binds
    fun bindsWakeRunRequester(requester: TestWakeRunRequester): WakeRunRequester
}
