// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.system.BackgroundUsage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBackgroundUsageRepository @Inject constructor() : BackgroundUsageRepository {
    private val settings = mutableMapOf<AppKey, BackgroundUsage>()
    var writeFailure: BackgroundUsageFailure? = null
    val reads = mutableListOf<AppKey>()
    val writes = mutableListOf<Pair<AppKey, Boolean>>()

    fun sendUsage(app: AppKey, usage: BackgroundUsage) {
        settings[app] = usage
    }

    override suspend fun read(app: AppKey): BackgroundUsageResult {
        reads += app
        return BackgroundUsageResult.Success(setting(app))
    }

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult {
        writes += app to allowed
        writeFailure?.let { return BackgroundUsageResult.Failure(it) }
        val before = setting(app)
        if (!before.changeable) return BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_CHANGEABLE)
        val after = before.copy(allowed = allowed)
        settings[app] = after
        return BackgroundUsageResult.Success(after)
    }

    private fun setting(app: AppKey) = settings[app] ?: BackgroundUsage(allowed = true, changeable = true)
}
