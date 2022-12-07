/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.rule

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import androidx.core.content.pm.PackageInfoCompat
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.entity.EComponentType
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.core.utils.StorageUtils
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.rule.entity.ComponentRule
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import com.merxury.ifw.entity.Rules
import com.merxury.ifw.util.RuleSerializer

object Rule {
    const val BLOCKER_RULE_MIME = "application/json"
    const val EXTENSION = ".json"
    const val IFW_EXTENSION = ".xml"
    private val logger = XLog.tag("Rule").build()

    suspend fun export(context: Context, packageName: String): Boolean {
        logger.i("Backup rules for $packageName")
        val pm = context.packageManager
        val applicationInfo = ApplicationUtil.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(
            packageName = applicationInfo.packageName,
            versionName = applicationInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(applicationInfo)
        )
        val ifwController = IntentFirewallImpl(packageName).load()
        try {
            applicationInfo.receivers?.forEach {
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm, ComponentName(it.packageName, it.name)
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        EComponentType.RECEIVER,
                        EControllerMethod.IFW
                    )
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        EComponentType.RECEIVER,
                        EControllerMethod.PM
                    )
                )
            }
            applicationInfo.services?.forEach {
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm, ComponentName(it.packageName, it.name)
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        EComponentType.SERVICE,
                        EControllerMethod.IFW
                    )
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        EComponentType.SERVICE,
                        EControllerMethod.PM
                    )
                )
            }
            applicationInfo.activities?.forEach {
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm, ComponentName(it.packageName, it.name)
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        stateIFW,
                        EComponentType.ACTIVITY,
                        EControllerMethod.IFW
                    )
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        EComponentType.ACTIVITY,
                        EControllerMethod.PM
                    )
                )
            }
            applicationInfo.providers?.forEach {
                val statePM = ApplicationUtil.checkComponentIsEnabled(
                    pm, ComponentName(it.packageName, it.name)
                )
                rule.components.add(
                    ComponentRule(
                        it.packageName,
                        it.name,
                        statePM,
                        EComponentType.PROVIDER,
                        EControllerMethod.PM
                    )
                )
            }
            val result = if (rule.components.isNotEmpty()) {
                StorageUtil.saveRuleToStorage(context, rule, packageName)
            } else {
                // No components exported, return true
                true
            }
            return result
        } catch (e: RuntimeException) {
            logger.e("Failed to export $packageName, ${e.message}")
            return false
        }
    }

    suspend fun import(context: Context, rule: BlockerRule): Boolean {
        val controllerType = PreferenceUtil.getControllerType(context)
        val controller = if (controllerType == EControllerMethod.IFW) {
            // Fallback to traditional controller
            ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
        } else {
            ComponentControllerProxy.getInstance(controllerType, context)
        }
        var ifwController: IntentFirewall? = null
        val pm = context.packageManager
        // Detects if contains IFW rules, if exists, create a new controller.
        rule.components.forEach ifwDetection@{
            if (it.method == EControllerMethod.IFW) {
                ifwController = IntentFirewallImpl(it.packageName).load()
                return@ifwDetection
            }
        }
        try {
            rule.components.forEach {
                when (it.method) {
                    EControllerMethod.IFW -> {
                        when (it.type) {
                            // state == false means that IFW applied
                            // We should add in the IFW controller
                            EComponentType.RECEIVER -> {
                                if (!it.state) {
                                    ifwController?.add(
                                        it.packageName, it.name, ComponentType.BROADCAST
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName, it.name, ComponentType.BROADCAST
                                    )
                                }
                            }

                            EComponentType.SERVICE -> {
                                if (!it.state) {
                                    ifwController?.add(
                                        it.packageName, it.name, ComponentType.SERVICE
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName, it.name, ComponentType.SERVICE
                                    )
                                }
                            }

                            EComponentType.ACTIVITY -> {
                                if (!it.state) {
                                    ifwController?.add(
                                        it.packageName, it.name, ComponentType.ACTIVITY
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName, it.name, ComponentType.ACTIVITY
                                    )
                                }
                            }
                            // content provider needs PM to implement it
                            EComponentType.PROVIDER -> {
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
                            pm, ComponentName(it.packageName, it.name)
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
            logger.e("Failed to import Blocker rule ${rule.packageName}, ${e.message}")
            return false
        }
        return true
    }

    suspend fun importIfwRules(context: Context): Int {
        val ifwBackupFolderUri = PreferenceUtil.getIfwRulePath(context)
        if (ifwBackupFolderUri == null) {
            logger.e("IFW folder hasn't been set yet.")
            return 0
        }
        val controller = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
        var succeedCount = 0
        val folder = DocumentFile.fromTreeUri(context, ifwBackupFolderUri)
        if (folder == null) {
            logger.e("Cannot open ifw backup folder")
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
        controller: IController
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

    fun resetIfw(): Boolean {
        var result = true
        try {
            val ifwFolder = StorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            files.forEach {
                if (!FileUtils.delete(it, false)) {
                    result = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(e.message)
            return false
        }
        return result
    }

    fun isApplicationUninstalled(
        context: Context,
        savedList: MutableList<String>,
        packageName: String
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
