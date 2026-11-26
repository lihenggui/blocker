// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.system.BackgroundUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBackgroundUsageRepository @Inject constructor() : BackgroundUsageRepository {
    private val defaultUsage = BackgroundUsage(allowed = true, changeable = true)
    private val settings = MutableStateFlow<Map<AppKey, BackgroundUsage?>>(emptyMap())
    var writeFailure: BackgroundUsageFailure? = null
    val reads = mutableListOf<AppKey>()
    val writes = mutableListOf<Pair<AppKey, Boolean>>()

    fun sendUsage(app: AppKey, usage: BackgroundUsage?) {
        settings.update { it + (app to usage) }
    }

    override fun observe(app: AppKey): Flow<BackgroundUsage?> = settings.map { it.getOrDefault(app, defaultUsage) }.distinctUntilChanged()

    override suspend fun read(app: AppKey): BackgroundUsageResult {
        reads += app
        val usage = settings.value.getOrDefault(app, defaultUsage)
        return if (usage != null) BackgroundUsageResult.Success(usage) else BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_INSTALLED)
    }

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult {
        writes += app to allowed
        writeFailure?.let { return BackgroundUsageResult.Failure(it) }
        val before = settings.value.getOrDefault(app, defaultUsage) ?: return BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_INSTALLED)
        if (!before.changeable) return BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_CHANGEABLE)
        val after = before.copy(allowed = allowed)
        sendUsage(app, after)
        return BackgroundUsageResult.Success(after)
    }
}
