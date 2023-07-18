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

package com.merxury.blocker.core.rule

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import com.merxury.blocker.core.controllers.ComponentControllerProxy
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.rule.entity.ComponentRule
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.core.ifw.IIntentFirewall
import timber.log.Timber

object Rule {
    const val BLOCKER_RULE_MIME = "application/json"
    const val EXTENSION = ".json"
    const val IFW_EXTENSION = ".xml"

    suspend fun export(
        context: Context,
        intentFirewall: IIntentFirewall,
        packageName: String,
        destUri: Uri,
    ): Boolean {
        Timber.i("Backup rules for $packageName")
        val pm = context.packageManager
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
                        ComponentType.RECEIVER,
                        ControllerType.IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        ComponentType.RECEIVER,
                        ControllerType.PM,
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
                        ComponentType.SERVICE,
                        ControllerType.IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        ComponentType.SERVICE,
                        ControllerType.PM,
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
                        ComponentType.ACTIVITY,
                        ControllerType.IFW,
                    ),
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        ComponentType.ACTIVITY,
                        ControllerType.PM,
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
                        ComponentType.PROVIDER,
                        ControllerType.PM,
                    ),
                )
            }
            val result = if (rule.components.isNotEmpty()) {
                StorageUtil.saveRuleToStorage(context, rule, packageName, destUri)
            } else {
                // No components exported, return true
                true
            }
            return result
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to export $packageName")
            return false
        }
    }

    suspend fun import(
        context: Context,
        intentFirewall: IIntentFirewall,
        rule: BlockerRule,
        controllerType: ControllerType,
    ): Boolean {
        val controller = if (controllerType == ControllerType.IFW) {
            // Fallback to traditional controller
            ComponentControllerProxy.getInstance(ControllerType.PM, context)
        } else {
            ComponentControllerProxy.getInstance(controllerType, context)
        }
        val pm = context.packageManager
        try {
            rule.components.forEach {
                when (it.method) {
                    ControllerType.IFW -> {
                        when (it.type) {
                            // content provider needs PM to implement it
                            ComponentType.PROVIDER -> {
                                if (!it.state) {
                                    controller.enable(it.packageName, it.name)
                                } else {
                                    controller.disable(it.packageName, it.name)
                                }
                            }
                            // state == false means that IFW applied
                            // We should add in the IFW controller
                            else -> {
                                if (!it.state) {
                                    intentFirewall.add(
                                        it.packageName,
                                        it.name,
                                    )
                                } else {
                                    intentFirewall.remove(
                                        it.packageName,
                                        it.name,
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // For PM controllers, state enabled means component is enabled
                        val currentState = ApplicationUtil.checkComponentIsEnabled(
                            pm,
                            ComponentName(it.packageName, it.name),
                        )
                        if (currentState == it.state) return@forEach
                        if (it.state) {
                            controller.enable(it.packageName, it.name)
                        } else {
                            controller.disable(it.packageName, it.name)
                        }
                    }
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to import Blocker rule ${rule.packageName}")
            return false
        }
        return true
    }

    fun isApplicationUninstalled(
        context: Context,
        savedList: MutableList<String>,
        packageName: String,
    ): Boolean {
        if (packageName.trim().isEmpty()) {
            return true
        }
        if (savedList.contains(packageName)) {
            return true
        }
        if (!ApplicationUtil.isAppInstalled(context.packageManager, packageName)) {
            savedList.add(packageName)
            return true
        }
        return false
    }
}
