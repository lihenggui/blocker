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

package com.merxury.blocker.core.domain

import android.content.ComponentName
import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.RootController
import com.merxury.blocker.core.controllers.shizuku.ShizukuController
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ImportBlockerRuleUseCase @Inject constructor(
    private val pm: PackageManager,
    private val rootController: RootController,
    private val ifwController: IfwController,
    private val shizukuController: ShizukuController,
    private val userDataRepository: UserDataRepository,
) {
    operator fun invoke(rule: BlockerRule): Flow<Int> = flow {
        val controllerType = userDataRepository.userData.first().controllerType
        val fallbackController = if (controllerType == PM) {
            rootController
        } else {
            shizukuController
        }
        var count = 0
        rule.components.forEach {
            if (it.method == IFW) {
                if (it.type == PROVIDER) {
                    // IFW controller did not support disabling provider
                    // Fallback to other controller
                    if (!it.state) {
                        fallbackController.enable(it.packageName, it.name)
                    } else {
                        fallbackController.disable(it.packageName, it.name)
                    }
                } else {
                    if (!it.state) {
                        ifwController.enable(it.packageName, it.name)
                    } else {
                        ifwController.disable(it.packageName, it.name)
                    }
                }
                count++
            } else {
                // For PM controllers, state enabled means component is enabled
                val currentState = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                if (currentState == it.state) return@forEach
                if (it.state) {
                    fallbackController.enable(it.packageName, it.name)
                } else {
                    fallbackController.disable(it.packageName, it.name)
                }
                count++
            }
        }
        emit(count)
    }
}
