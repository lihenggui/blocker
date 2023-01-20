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
import android.content.pm.ComponentInfo
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.documentfile.provider.DocumentFile
import com.merxury.blocker.core.controllers.ComponentControllerProxy
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.rule.entity.ComponentRule
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.IfwComponentType
import com.merxury.ifw.entity.Rules
import com.merxury.ifw.util.IfwStorageUtils
import com.merxury.ifw.util.RuleSerializer
import timber.log.Timber

object Rule {
    const val BLOCKER_RULE_MIME = "application/json"
    const val EXTENSION = ".json"
    const val IFW_EXTENSION = ".xml"

    suspend fun export(context: Context, packageName: String, destUri: Uri): Boolean {
        Timber.i("Backup rules for $packageName")
        val pm = context.packageManager
        val applicationInfo = ApplicationUtil.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(
            packageName = applicationInfo.packageName,
            versionName = applicationInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(applicationInfo),
        )
        val ifwController = IntentFirewallImpl(packageName).load()
        try {
            applicationInfo.receivers?.forEach {
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
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
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
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
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
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
            Timber.e("Failed to export $packageName", e)
            return false
        }
    }

    suspend fun import(
        context: Context,
        rule: BlockerRule,
        controllerType: ControllerType,
    ): Boolean {
        val controller = if (controllerType == ControllerType.IFW) {
            // Fallback to traditional controller
            ComponentControllerProxy.getInstance(ControllerType.PM, context)
        } else {
            ComponentControllerProxy.getInstance(controllerType, context)
        }
        var ifwController: IntentFirewall? = null
        val pm = context.packageManager
        // Detects if contains IFW rules, if exists, create a new controller.
        rule.components.forEach ifwDetection@{
            if (it.method == ControllerType.IFW) {
                ifwController = IntentFirewallImpl(it.packageName).load()
                return@ifwDetection
            }
        }
        try {
            rule.components.forEach {
                when (it.method) {
                    ControllerType.IFW -> {
                        when (it.type) {
                            // state == false means that IFW applied
                            // We should add in the IFW controller
                            ComponentType.RECEIVER -> {
                                if (!it.state) {
                                    ifwController?.add(
                                        it.packageName,
                                        it.name,
                                        IfwComponentType.BROADCAST,
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName,
                                        it.name,
                                        IfwComponentType.BROADCAST,
                                    )
                                }
                            }

                            ComponentType.SERVICE -> {
                                if (!it.state) {
                                    ifwController?.add(
                                        it.packageName,
                                        it.name,
                                        IfwComponentType.SERVICE,
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName,
                                        it.name,
                                        IfwComponentType.SERVICE,
                                    )
                                }
                            }

                            ComponentType.ACTIVITY -> {
                                if (!it.state) {
                                    ifwController?.add(
                                        it.packageName,
                                        it.name,
                                        IfwComponentType.ACTIVITY,
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName,
                                        it.name,
                                        IfwComponentType.ACTIVITY,
                                    )
                                }
                            }
                            // content provider needs PM to implement it
                            ComponentType.PROVIDER -> {
                                if (!it.state) {
                                    controller.enable(it.packageName, it.name)
                                } else {
                                    controller.disable(it.packageName, it.name)
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
            ifwController?.save()
        } catch (e: RuntimeException) {
            Timber.e("Failed to import Blocker rule ${rule.packageName}, ${e.message}")
            return false
        }
        return true
    }

    suspend fun importIfwRules(context: Context, importFolderUri: Uri): Int {
        val controller = ComponentControllerProxy.getInstance(ControllerType.IFW, context)
        var succeedCount = 0
        val folder = DocumentFile.fromTreeUri(context, importFolderUri)
        if (folder == null) {
            Timber.e("Cannot open ifw backup folder")
            return 0
        }
        // { file -> file.isFile && file.name.endsWith(".xml") }
        folder.listFiles().forEach { documentFile ->
            if (!documentFile.isFile) {
                return@forEach
            }
            context.contentResolver.openInputStream(documentFile.uri)?.use { stream ->
                val rule = RuleSerializer.deserialize(stream) ?: return@forEach
                updateIfwState(rule, controller)
                succeedCount++
            }
        }
        return succeedCount
    }

    suspend fun updateIfwState(
        rule: Rules,
        controller: IController,
    ) {
        val activities =
            rule.activity?.componentFilters?.asSequence()?.map { filter -> filter.name.split("/") }
                ?.map { names ->
                    val component = ComponentInfo()
                    component.packageName = names[0]
                    component.name = names[1]
                    component
                }?.toList() ?: mutableListOf()
        val broadcast =
            rule.broadcast?.componentFilters?.asSequence()?.map { filter -> filter.name.split("/") }
                ?.map { names ->
                    val component = ComponentInfo()
                    component.packageName = names[0]
                    component.name = names[1]
                    component
                }?.toList() ?: mutableListOf()
        val service =
            rule.service?.componentFilters?.asSequence()?.map { filter -> filter.name.split("/") }
                ?.map { names ->
                    val component = ComponentInfo()
                    component.packageName = names[0]
                    component.name = names[1]
                    component
                }?.toList() ?: mutableListOf()
        controller.batchDisable(activities) {}
        controller.batchDisable(broadcast) {}
        controller.batchDisable(service) {}
    }

    suspend fun resetIfw(): Boolean {
        var result = true
        try {
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            files.forEach {
                if (!FileUtils.delete(it, false)) {
                    result = false
                }
            }
        } catch (e: Exception) {
            Timber.e("Can't reset IFW", e)
            return false
        }
        return result
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
