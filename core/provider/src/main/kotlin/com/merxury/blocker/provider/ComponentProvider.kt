/*
 * Copyright 2024 Blocker
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
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class ComponentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ComponentRepositoryEntryPoint {
        fun componentRepository(): ComponentRepository

        fun analyticsHelper(): AnalyticsHelper
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            "getComponents" -> getBlockedComponents(arg)
            "blocks" -> controlComponent(arg, extras)
            else -> null
        }
    }

    private fun getBlockedComponents(packageName: String?): Bundle? = runBlocking {
        if (packageName == null) return@runBlocking null
        val appContext = context?.applicationContext ?: return@runBlocking null
        val hintEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            ComponentRepositoryEntryPoint::class.java,
        )
        // Do not get data from the DB directly, because the data may be uninitialized
        val repository = hintEntryPoint.componentRepository()
        val blockedComponents = repository.getComponentList(packageName).first()
            .filter { it.ifwBlocked || it.pmBlocked }
            .map {
                ShareCmpInfo.Component(
                    it.packageName,
                    it.name,
                    block = true,
                )
            }
        val returnJson = Json.encodeToString(ShareCmpInfo(packageName, blockedComponents))
        return@runBlocking bundleOf("cmp_list" to returnJson)
    }

    private fun controlComponent(packageName: String?, data: Bundle?): Bundle? = runBlocking {
        if (packageName == null || data == null) return@runBlocking null
        val rawString = data.getString("cmp_list") ?: return@runBlocking null
        val appContext = context?.applicationContext ?: return@runBlocking null
        val hintEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            ComponentRepositoryEntryPoint::class.java,
        )
        val componentRepository = hintEntryPoint.componentRepository()
        val analyticsHelper = hintEntryPoint.analyticsHelper()
        try {
            val shareCmpInfo = Json.decodeFromString<ShareCmpInfo>(rawString)
            Timber.d("controlComponent: $shareCmpInfo")
            shareCmpInfo.components.forEach { component ->
                val blockerComponent = ComponentInfo(
                    name = component.name,
                    packageName = packageName,
                    // The controller doesn't care about the type of the component
                    // It will query internally, so we just set it to ACTIVITY
                    // Just to avoid compilation error
                    type = ACTIVITY,
                )
                componentRepository.controlComponent(
                    blockerComponent,
                    newState = !component.block,
                ).first()
                analyticsHelper.logControlComponentViaProvider(newState = !component.block)
            }
            // Returned, but seems that it's not used.
            return@runBlocking data
        } catch (e: Exception) {
            Timber.e(e, "Error in controlComponent")
            return@runBlocking null
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

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

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.item/vnd.com.merxury.blocker.component"
    }
}
