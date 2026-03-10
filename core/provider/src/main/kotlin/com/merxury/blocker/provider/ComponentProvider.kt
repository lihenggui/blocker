/*
 * Copyright 2025 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import timber.log.Timber

class ComponentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ComponentRepositoryEntryPoint {
        fun componentRepository(): ComponentRepository

        fun analyticsHelper(): AnalyticsHelper

        fun json(): Json

        @Dispatcher(IO)
        fun ioDispatcher(): CoroutineDispatcher
    }

    private fun entryPoint(): ComponentRepositoryEntryPoint? {
        val appContext = context?.applicationContext ?: return null
        return EntryPointAccessors.fromApplication(
            appContext,
            ComponentRepositoryEntryPoint::class.java,
        )
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        METHOD_GET_COMPONENTS -> getBlockedComponents(arg)
        METHOD_BLOCK_COMPONENTS -> controlComponent(arg, extras)
        else -> null
    }

    private fun getBlockedComponents(packageName: String?): Bundle? {
        if (packageName == null) return null
        val ep = entryPoint() ?: return null
        return runBlocking(ep.ioDispatcher()) {
            // Do not get data from the DB directly, because the data may be uninitialized
            val blockedComponents = ep.componentRepository()
                .getComponentList(packageName).first()
                .filter { it.ifwBlocked || it.pmBlocked }
                .map {
                    ShareCmpInfo.Component(
                        type = it.type.name,
                        name = it.name,
                        block = true,
                    )
                }
            val returnJson = ep.json().encodeToString(
                ShareCmpInfo.serializer(),
                ShareCmpInfo(packageName, blockedComponents),
            )
            bundleOf(KEY_COMPONENT_LIST to returnJson)
        }
    }

    private fun controlComponent(packageName: String?, data: Bundle?): Bundle? {
        if (packageName == null || data == null) return null
        val rawString = data.getString(KEY_COMPONENT_LIST) ?: return null
        val ep = entryPoint() ?: return null
        return runBlocking(ep.ioDispatcher()) {
            try {
                val shareCmpInfo = ep.json().decodeFromString<ShareCmpInfo>(rawString)
                Timber.d("controlComponent: $shareCmpInfo")
                var successCount = 0
                shareCmpInfo.components.forEach { component ->
                    val componentType = try {
                        ComponentType.valueOf(component.type)
                    } catch (_: IllegalArgumentException) {
                        ComponentType.ACTIVITY
                    }
                    val blockerComponent = ComponentInfo(
                        name = component.name,
                        packageName = packageName,
                        type = componentType,
                    )
                    val result = ep.componentRepository().controlComponent(
                        blockerComponent,
                        newState = !component.block,
                    ).first()
                    if (result) successCount++
                    ep.analyticsHelper().logControlComponentViaProvider(
                        newState = !component.block,
                    )
                }
                bundleOf(
                    KEY_SUCCESS_COUNT to successCount,
                    KEY_TOTAL_COUNT to shareCmpInfo.components.size,
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in controlComponent")
                null
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        // Not implemented
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Not implemented
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        // Not implemented
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not implemented
        return 0
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.merxury.blocker.component"

    companion object {
        const val METHOD_GET_COMPONENTS = "getComponents"
        const val METHOD_BLOCK_COMPONENTS = "blocks"
        const val KEY_COMPONENT_LIST = "cmp_list"
        const val KEY_SUCCESS_COUNT = "success_count"
        const val KEY_TOTAL_COUNT = "total_count"
    }
}
