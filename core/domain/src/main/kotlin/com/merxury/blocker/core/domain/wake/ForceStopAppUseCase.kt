// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import javax.inject.Inject

class ForceStopAppUseCase @Inject constructor(
    private val appControl: AppControl,
) {
    suspend operator fun invoke(packageName: String): CallResult<Unit> = appControl.forceStop(AppKey(packageName, UserId.SYSTEM))
}
