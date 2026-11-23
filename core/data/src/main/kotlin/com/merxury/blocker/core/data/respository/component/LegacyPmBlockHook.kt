/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.data.respository.component

import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.data.device.ownUser
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.system.SystemPackageReader
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Keeps the record of Blocker's PackageManager blocks up to date for changes the old screens make
 * through the old controllers, which bypass ComponentStatusRepository.control.
 *
 * Temporary: remove it with [LocalComponentRepository] once every screen controls components
 * through ComponentStatusRepository and the old ComponentRepository is deleted.
 */
internal fun interface LegacyPmBlockHook {
    /**
     * Called after [LocalComponentRepository] changed [components] of one package, which all
     * belong to the user Blocker runs in, to [enabled] with [controllerType] and read them back,
     * also when the change failed. Never throws except for cancellation.
     */
    suspend fun afterControl(components: List<ComponentInfo>, enabled: Boolean, controllerType: ControllerType)
}

/**
 * [LegacyPmBlockHook] that reads the components' PackageManager settings again, telling DISABLED
 * from a manifest's own default, which the old read-back cannot. A disable records each component
 * whose PackageManager setting it changed, every one except with [ControllerType.IFW], which
 * changes only providers there, and that reads DISABLED. An enable, which clears PackageManager
 * blocks with every controller type, drops each that reads DEFAULT or ENABLED. A setting that
 * cannot be read changes no record.
 */
internal class RecordingLegacyPmBlockHook @Inject constructor(
    private val reader: SystemPackageReader,
    private val records: PmBlockRecords,
) : LegacyPmBlockHook {
    override suspend fun afterControl(components: List<ComponentInfo>, enabled: Boolean, controllerType: ControllerType) {
        val touched = if (enabled || controllerType != ControllerType.IFW) components else components.filter { it.type == ComponentType.PROVIDER }
        if (touched.isEmpty()) return
        val user = ownUser()
        val app = AppKey(touched.first().packageName, user)
        try {
            val settings = when (val read = reader.pmSettings(app, touched.map { ComponentId(it.packageName, it.name) })) {
                is ReadResult.Success -> read.value

                is ReadResult.Failure -> {
                    Timber.w("Cannot read the PM settings of $app after an old control, keep the PM block records: ${read.reason}")
                    return
                }
            }
            val keys = settings.filterValues { if (enabled) it == PmSetting.DEFAULT || it == PmSetting.ENABLED else it == PmSetting.DISABLED }.keys.map { ComponentKey(it, user) }
            if (enabled) records.forget(keys) else records.record(keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot update the PM block records of $app after an old control")
        }
    }
}
