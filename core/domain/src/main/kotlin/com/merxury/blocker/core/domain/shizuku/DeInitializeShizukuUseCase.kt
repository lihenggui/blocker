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

package com.merxury.blocker.core.domain.shizuku

import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class DeInitializeShizukuUseCase @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val shizukuInitializer: IShizukuInitializer,
) {
    suspend operator fun invoke() {
        val controllerType = userDataRepository.userData.first().controllerType
        if (controllerType == SHIZUKU) {
            Timber.i("DeInitialize Shizuku")
            shizukuInitializer.unregisterShizuku()
        }
    }
}
