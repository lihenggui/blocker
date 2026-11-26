// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.di

import com.merxury.blocker.core.data.wake.WakeRunRequester
import com.merxury.blocker.sync.workers.WakeWork
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface WakeRunModule {
    @Binds
    fun bindsWakeRunRequester(work: WakeWork): WakeRunRequester
}
