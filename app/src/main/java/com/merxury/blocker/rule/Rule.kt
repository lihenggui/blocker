package com.merxury.blocker.rule

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.rule.entity.ComponentRule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.ui.component.EComponentType
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import com.merxury.ifw.util.RuleSerializer
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.FileUtils
import com.merxury.libkit.utils.StorageUtils
import java.io.File

object Rule {
    const val BLOCKER_RULE_MIME = "application/json"
    const val EXTENSION = ".json"
    private val logger = XLog.tag("Rule").build()

    suspend fun export(context: Context, packageName: String): Boolean {
        logger.i("Backup rules for $packageName")
        val pm = context.packageManager
        val applicationInfo = ApplicationUtil.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(
            packageName = applicationInfo.packageName,
            versionName = applicationInfo.versionName,
            versionCode = applicationInfo.versionCode
        )
        val ifwController = IntentFirewallImpl.getInstance(context, packageName)
        try {
            applicationInfo.receivers?.forEach {
                val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
                val statePM =
                    ApplicationUtil.checkComponentIsEnabled(
                        pm,
                        ComponentName(it.packageName, it.name)
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
                val statePM =
                    ApplicationUtil.checkComponentIsEnabled(
                        pm,
                        ComponentName(it.packageName, it.name)
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
                val statePM =
                    ApplicationUtil.checkComponentIsEnabled(
                        pm,
                        ComponentName(it.packageName, it.name)
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
                val statePM =
                    ApplicationUtil.checkComponentIsEnabled(
                        pm,
                        ComponentName(it.packageName, it.name)
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
            if (rule.components.isNotEmpty()) {
                StorageUtil.saveRuleToStorage(context, rule, packageName)
            }
            return true
        } catch (e: RuntimeException) {
            logger.e("Failed to export $packageName, ${e.message}")
            return false
        }
    }

    fun import(context: Context, rule: BlockerRule): Boolean {
        val controller = getController(context)
        var ifwController: IntentFirewall? = null
        // Detects if contains IFW rules, if exists, create a new controller.
        rule.components.forEach ifwDetection@{
            if (it.method == EControllerMethod.IFW) {
                ifwController = IntentFirewallImpl.getInstance(context, rule.packageName)
                return@ifwDetection
            }
        }
        try {
            rule.components.forEach {
                when (it.method) {
                    EControllerMethod.IFW -> {
                        when (it.type) {
                            EComponentType.RECEIVER -> {
                                if (it.state) {
                                    ifwController?.add(
                                        it.packageName,
                                        it.name,
                                        ComponentType.BROADCAST
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName,
                                        it.name,
                                        ComponentType.BROADCAST
                                    )
                                }
                            }
                            EComponentType.SERVICE -> {
                                if (it.state) {
                                    ifwController?.add(
                                        it.packageName,
                                        it.name,
                                        ComponentType.SERVICE
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName,
                                        it.name,
                                        ComponentType.SERVICE
                                    )
                                }
                            }
                            EComponentType.ACTIVITY -> {
                                if (it.state) {
                                    ifwController?.add(
                                        it.packageName,
                                        it.name,
                                        ComponentType.ACTIVITY
                                    )
                                } else {
                                    ifwController?.remove(
                                        it.packageName,
                                        it.name,
                                        ComponentType.ACTIVITY
                                    )
                                }
                            }
                            // content provider needs PM to implement it
                            EComponentType.PROVIDER -> {
                                if (it.state) {
                                    controller.enable(it.packageName, it.name)
                                } else {
                                    controller.disable(it.packageName, it.name)
                                }
                            }
                            EComponentType.UNKNOWN -> {
                            }
                        }
                    }
                    else -> {
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

    fun importMatRules(
        context: Context,
        file: File,
        action: (context: Context, name: String, current: Int, total: Int) -> Unit
    ): RulesResult {
        var succeedCount = 0
        var failedCount = 0
        val total = countLines(file)
        val controller = getController(context)
        val uninstalledAppList = mutableListOf<String>()
        try {
            file.forEachLine {
                if (it.trim().isEmpty() || !it.contains("/")) {
                    failedCount++
                    return@forEachLine
                }
                val splitResult = it.split("/")
                if (splitResult.size != 2) {
                    failedCount++
                    return@forEachLine
                }
                val packageName = splitResult[0]
                val name = splitResult[1]
                if (isApplicationUninstalled(context, uninstalledAppList, packageName)) {
                    failedCount++
                    return@forEachLine
                }
                val result = controller.disable(packageName, name)
                if (result) {
                    succeedCount++
                } else {
                    logger.d("Failed to change component state for : $it")
                    failedCount++
                }
                action(context, name, (succeedCount + failedCount), total)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(e.message)
            return RulesResult(false, succeedCount, failedCount)
        }
        return RulesResult(true, succeedCount, failedCount)
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
                val activities = rule.activity?.componentFilters
                    ?.asSequence()
                    ?.map { filter -> filter.name.split("/") }
                    ?.map { names ->
                        val component = ComponentInfo()
                        component.packageName = names[0]
                        component.name = names[1]
                        component
                    }
                    ?.toList() ?: mutableListOf()
                val broadcast = rule.broadcast?.componentFilters
                    ?.asSequence()
                    ?.map { filter -> filter.name.split("/") }
                    ?.map { names ->
                        val component = ComponentInfo()
                        component.packageName = names[0]
                        component.name = names[1]
                        component
                    }
                    ?.toList() ?: mutableListOf()
                val service = rule.service?.componentFilters
                    ?.asSequence()
                    ?.map { filter -> filter.name.split("/") }
                    ?.map { names ->
                        val component = ComponentInfo()
                        component.packageName = names[0]
                        component.name = names[1]
                        component
                    }
                    ?.toList() ?: mutableListOf()
                controller.batchDisable(activities) {}
                controller.batchDisable(broadcast) {}
                controller.batchDisable(service) {}
                succeedCount++
            }
        }
        return succeedCount
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

    private fun countLines(file: File): Int {
        var lines = 0
        if (!file.exists()) {
            return lines
        }
        file.forEachLine {
            if (it.trim().isEmpty()) {
                return@forEachLine
            }
            lines++
        }
        return lines
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

    private fun getController(context: Context): IController {
        val controllerType = PreferenceUtil.getControllerType(context)
        return ComponentControllerProxy.getInstance(controllerType, context)
    }
}
