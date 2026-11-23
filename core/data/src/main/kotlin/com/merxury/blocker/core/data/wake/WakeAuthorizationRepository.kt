// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.controllers.wake.authorizedShizukuUid
import com.merxury.blocker.core.controllers.wake.observeWakeShizukuAuthorization
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.utils.LibrootRootAvailabilityChecker
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class WakeAuthorizationRepository @Inject constructor(
    private val root: LibrootRootAvailabilityChecker,
    private val userData: UserDataRepository,
) {
    val rootGranted get() = root.rootGranted
    val shizukuUid get() = authorizedShizukuUid()

    fun observeShizukuAuthorization(onChanged: (Int?) -> Unit) = observeWakeShizukuAuthorization(onChanged)

    suspend fun checkForegroundRoot() {
        if (userData.userData.first().controllerType != ControllerType.SHIZUKU) root.isRootAvailable()
    }
}
