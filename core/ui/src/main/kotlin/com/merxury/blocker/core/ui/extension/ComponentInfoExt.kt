/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.ui.extension

import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.result.Result
import timber.log.Timber

/**
 * Utility function to update the switch state of a [ComponentSearchResult]
 * It is used in doing async operations on the switch button and updating the state of the ui
 * before the actual operation is completed
 */
fun Result<ComponentSearchResult>.updateComponentInfoSwitchState(
    changed: List<ComponentInfo>,
    controllerType: ControllerType,
    enabled: Boolean,
): Result<ComponentSearchResult> {
    // If the result is not success, return the result as it is
    if (this !is Result.Success) {
        Timber.w("Wrong UI state: $this, cannot update switch state.")
        return this
    }
    // Find the matching components in the list and change the ifwBlocked to the new value
    val activity = getUpdatedListState(this.data.activity, changed, controllerType, enabled)
    val service = getUpdatedListState(this.data.service, changed, controllerType, enabled)
    val receiver = getUpdatedListState(this.data.receiver, changed, controllerType, enabled)
    val provider = getUpdatedListState(this.data.provider, changed, controllerType, enabled)
    return Result.Success(
        data.copy(
            activity = activity,
            service = service,
            receiver = receiver,
            provider = provider,
        ),
    )
}

/**
 * Updated changed items in he current list with the new value
 * In order to update UI asynchronously
 */
private fun getUpdatedListState(
    current: List<ComponentInfo>,
    change: List<ComponentInfo>,
    controllerType: ControllerType,
    enabled: Boolean,
): List<ComponentInfo> {
    val updatedItems = current.filter { currentItem ->
        change.any { changedItem ->
            currentItem.packageName == changedItem.packageName && currentItem.name == changedItem.name
        }
    }.map {
        if (controllerType == IFW) {
            it.copy(ifwBlocked = !enabled)
        } else {
            it.copy(pmBlocked = !enabled)
        }
    }
    // Update the current list, replace the updated items with the old items
    return current.map { currentItem ->
        updatedItems.find {
            it.packageName == currentItem.packageName && it.name == currentItem.name
        } ?: currentItem
    }
}

fun Result<ComponentSearchResult>.updateComponentDetailUiState(
    detail: ComponentDetail,
): Result<ComponentSearchResult> {
    if (this !is Result.Success) {
        Timber.w("Wrong UI state: $this, cannot update component detail.")
        return this
    }
    val componentItem = data.activity.find {
        it.name == detail.name
    } ?: data.service.find {
        it.name == detail.name
    } ?: data.receiver.find {
        it.name == detail.name
    } ?: data.provider.find {
        it.name == detail.name
    }
    if (componentItem == null) {
        Timber.w("Cannot find component with name ${detail.name}, return empty result")
        return this
    }
    val updatedItem = componentItem.copy(
        description = detail.description,
    )
    val updatedList = when (componentItem.type) {
        ACTIVITY -> data.activity
        SERVICE -> data.service
        RECEIVER -> data.receiver
        PROVIDER -> data.provider
    }.map {
        if (it.name == updatedItem.name) {
            updatedItem
        } else {
            it
        }
    }
    val updatedUiState = when (updatedItem.type) {
        ACTIVITY -> data.copy(activity = updatedList)
        SERVICE -> data.copy(service = updatedList)
        RECEIVER -> data.copy(receiver = updatedList)
        PROVIDER -> data.copy(provider = updatedList)
    }
    return Result.Success(updatedUiState)
}
