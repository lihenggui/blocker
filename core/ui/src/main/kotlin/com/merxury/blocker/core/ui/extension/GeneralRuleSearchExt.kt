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

package com.merxury.blocker.core.ui.extension

import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.result.Result
import timber.log.Timber

/**
 * Utility function to update the switch state of a Result<List<MatchedItem>>
 * It is used in doing async operations on the switch button and updating the state of the ui
 * before the actual operation is completed
 */
fun Result<List<MatchedItem>>.updateComponentInfoSwitchState(
    changed: List<ComponentInfo>,
    controllerType: ControllerType,
    enabled: Boolean,
): Result<List<MatchedItem>> {
    if (this !is Result.Success) {
        Timber.w("Wrong UI state: $this, cannot update switch state.")
        return this
    }
    val updatedDataList = data.map { item ->
        val currentList = item.componentList
        val updatedItems = item.componentList.filter { currentItem ->
            changed.any { changedItem ->
                currentItem.packageName == changedItem.packageName && currentItem.name == changedItem.name
            }
        }.map {
            if (controllerType == IFW) {
                it.copy(ifwBlocked = !enabled)
            } else {
                it.copy(pmBlocked = !enabled)
            }
        }
        val updatedList = if (updatedItems.isEmpty()) {
            currentList
        } else {
            currentList.map { currentItem ->
                updatedItems.find {
                    it.packageName == currentItem.packageName && it.name == currentItem.name
                } ?: currentItem
            }
        }
        MatchedItem(
            header = item.header,
            componentList = updatedList,
        )
    }
    return Result.Success(updatedDataList)
}

/**
 * Utility function to update the component detail in a screen without reload the data
 */
fun Result<List<MatchedItem>>.updateComponentDetailUiState(
    detail: ComponentDetail,
): Result<List<MatchedItem>> {
    if (this !is Result.Success) {
        Timber.w("Wrong UI state: $this, cannot update component detail.")
        return this
    }
    val updatedDataList = data.map { item ->
        val currentList = item.componentList
        val updatedItems = currentList.filter { currentItem ->
            currentItem.name == detail.name
        }.map {
            it.copy(
                description = detail.description,
            )
        }
        val updatedList = if (updatedItems.isEmpty()) {
            currentList
        } else {
            currentList.map { currentItem ->
                updatedItems.find {
                    it.name == currentItem.name
                } ?: currentItem
            }
        }
        MatchedItem(
            header = item.header,
            componentList = updatedList,
        )
    }
    return Result.Success(updatedDataList)
}
