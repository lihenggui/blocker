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
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.rule.entity.ComponentRule
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.core.ifw.IIntentFirewall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class ExportBlockerRuleUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pm: PackageManager,
    private val intentFirewall: IIntentFirewall,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String, destUri: Uri): Flow<Boolean> = flow {
        Timber.i("Export Blocker rules for $packageName")
        val applicationInfo = ApplicationUtil.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(
            packageName = applicationInfo.packageName,
            versionName = applicationInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(applicationInfo),
        )
        try {
            applicationInfo.receivers?.forEach {
                val stateIFW = intentFirewall.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        RECEIVER,
                        IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        RECEIVER,
                        PM,
                    ),
                )
            }
            applicationInfo.services?.forEach {
                val stateIFW = intentFirewall.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        SERVICE,
                        IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        SERVICE,
                        PM,
                    ),
                )
            }
            applicationInfo.activities?.forEach {
                val stateIFW = intentFirewall.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        ACTIVITY,
                        IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        ACTIVITY,
                        PM,
                    ),
                )
            }
            applicationInfo.providers?.forEach {
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(it.packageName, it.name),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        PROVIDER,
                        PM,
                    ),
                )
            }
            val result = if (rule.components.isNotEmpty()) {
                StorageUtil.saveRuleToStorage(context, rule, packageName, destUri, ioDispatcher)
            } else {
                // No components exported, return true
                true
            }
            emit(result)
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to export $packageName")
            emit(false)
        }
    }
}
