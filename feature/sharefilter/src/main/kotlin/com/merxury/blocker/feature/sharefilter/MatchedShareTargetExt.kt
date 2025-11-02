/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.sharefilter

import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.result.Result
import timber.log.Timber

fun Result<List<MatchedShareTarget>>.updateShareTargetSwitchState(
    changed: List<ShareTargetActivityEntity>,
    controllerType: ControllerType,
    enabled: Boolean,
): Result<List<MatchedShareTarget>> {
    if (this !is Result.Success) {
        Timber.w("Wrong UI state: $this, cannot update switch state.")
        return this
    }

    val updatedList = data.map { matchedShareTarget ->
        val updatedTargets = matchedShareTarget.shareTargets.map { uiItem ->
            val shouldUpdate = changed.any { changedEntity ->
                changedEntity.packageName == uiItem.entity.packageName &&
                    changedEntity.componentName == uiItem.entity.componentName
            }
            if (shouldUpdate) {
                val updatedEntity = if (controllerType == IFW) {
                    uiItem.entity.copy(ifwBlocked = !enabled)
                } else {
                    uiItem.entity.copy(pmBlocked = !enabled)
                }
                uiItem.copy(entity = updatedEntity)
            } else {
                uiItem
            }
        }
        matchedShareTarget.copy(shareTargets = updatedTargets)
    }

    return Result.Success(updatedList)
}
