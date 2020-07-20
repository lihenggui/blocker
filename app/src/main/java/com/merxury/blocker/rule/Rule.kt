package com.merxury.blocker.rule

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.RequiresApi
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.merxury.blocker.R
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.rule.entity.ComponentRule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.ui.component.EComponentType
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import com.merxury.ifw.util.RuleSerializer
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.FileUtils
import com.merxury.libkit.utils.StorageUtils
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object Rule {
    const val EXTENSION = ".json"
    private val logger = XLog.tag("Rule").build()

    // TODO remove template code
    fun export(context: Context, packageName: String): RulesResult {
        logger.i("Backup rules for $packageName")
        val pm = context.packageManager
        val applicationInfo = ApplicationUtil.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(packageName = applicationInfo.packageName, versionName = applicationInfo.versionName, versionCode = applicationInfo.versionCode)
        var disabledComponentsCount = 0
        val ifwController = IntentFirewallImpl.getInstance(context, packageName)
        applicationInfo.receivers?.forEach {
            val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
            val statePM = ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))
            rule.components.add(ComponentRule(it.packageName, it.name, stateIFW, EComponentType.RECEIVER, EControllerMethod.IFW))
            rule.components.add(ComponentRule(it.packageName, it.name, statePM, EComponentType.RECEIVER, EControllerMethod.PM))
            disabledComponentsCount++
        }
        applicationInfo.services?.forEach {
            val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
            val statePM = ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))
            rule.components.add(ComponentRule(it.packageName, it.name, stateIFW, EComponentType.SERVICE, EControllerMethod.IFW))
            rule.components.add(ComponentRule(it.packageName, it.name, statePM, EComponentType.SERVICE, EControllerMethod.PM))
            disabledComponentsCount++
        }
        applicationInfo.activities?.forEach {
            val stateIFW = ifwController.getComponentEnableState(it.packageName, it.name)
            val statePM = ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))
            rule.components.add(ComponentRule(it.packageName, it.name, stateIFW, EComponentType.ACTIVITY, EControllerMethod.IFW))
            rule.components.add(ComponentRule(it.packageName, it.name, statePM, EComponentType.ACTIVITY, EControllerMethod.PM))
            disabledComponentsCount++
        }
        applicationInfo.providers?.forEach {
            val statePM = ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))
            rule.components.add(ComponentRule(it.packageName, it.name, statePM, EComponentType.PROVIDER, EControllerMethod.PM))
            disabledComponentsCount++
        }
        return if (rule.components.isNotEmpty()) {
            val ruleFile = File(getBlockerRuleFolder(context), packageName + EXTENSION)
            saveRuleToStorage(rule, ruleFile)
            if (Build.VERSION.SDK_INT > 28) FileUtils.getExternalStorageMove(getBlockerRuleFolder(context).absolutePath, getBlockerExternalFolder(context, true))
            RulesResult(true, disabledComponentsCount, 0)
        } else {
            RulesResult(false, 0, 0)
        }
    }

    fun import(context: Context, file: File): RulesResult {
        if (Build.VERSION.SDK_INT > 28) FileUtils.getExternalStorageMove(getBlockerExternalFolder(context, true), getBlockerRuleFolder(context).absolutePath)
        val jsonReader = JsonReader(FileReader(file))
        val appRule = Gson().fromJson<BlockerRule>(jsonReader, BlockerRule::class.java)
                ?: return RulesResult(false, 0, 0)
        var succeedCount = 0
        var failedCount = 0
        val total = appRule.components.size
        val controller = getController(context)
        var ifwController: IntentFirewall? = null
        // Detects if contains IFW rules, if exists, create a new controller.
        appRule.components.forEach ifwDetection@{
            if (it.method == EControllerMethod.IFW) {
                ifwController = IntentFirewallImpl.getInstance(context, appRule.packageName)
                return@ifwDetection
            }
        }
        try {
            appRule.components.forEach {
                val controllerResult = when (it.method) {
                    EControllerMethod.IFW -> {
                        when (it.type) {
                            EComponentType.RECEIVER -> {
                                if (it.state) {
                                    ifwController?.add(it.packageName, it.name, ComponentType.BROADCAST) ?: false
                                } else {
                                    ifwController?.remove(it.packageName, it.name, ComponentType.BROADCAST) ?: false
                                }
                            }
                            EComponentType.SERVICE -> {
                                if (it.state) {
                                    ifwController?.add(it.packageName, it.name, ComponentType.SERVICE) ?: false
                                } else {
                                    ifwController?.remove(it.packageName, it.name, ComponentType.SERVICE) ?: false
                                }
                            }
                            EComponentType.ACTIVITY -> {
                                if (it.state) {
                                    ifwController?.add(it.packageName, it.name, ComponentType.ACTIVITY) ?: false
                                } else {
                                    ifwController?.remove(it.packageName, it.name, ComponentType.ACTIVITY) ?: false
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
                            EComponentType.UNKNOWN -> false
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
                if (controllerResult) {
                    succeedCount++
                } else {
                    failedCount++
                }
            }
            ifwController?.save()
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(e.message)
            return RulesResult(false, succeedCount, failedCount)
        }
        return RulesResult(true, succeedCount, failedCount)
    }

    fun exportAll(context: Context) {
        val appList = ApplicationUtil.getThirdPartyApplicationList(context)
        appList.forEach {
            val packageName = it.packageName
            export(context, packageName)
        }
    }

    fun importAll(context: Context) {
        val appList = ApplicationUtil.getThirdPartyApplicationList(context)
        appList.forEach {
            val packageName = it.packageName
            val file = File(getBlockerRuleFolder(context), packageName + EXTENSION)
            if (file.exists()) {
                file.delete()
            }
            import(context, file)
        }
    }

    fun importMatRules(context: Context, file: File, action: (context: Context, name: String, current: Int, total: Int) -> Unit): RulesResult {
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

    fun exportIfwRules(context: Context): Int {
        val ifwFolder = StorageUtils.getIfwFolder()
        val ifwBackupFolder = getBlockerIFWFolder(context)
        val files = FileUtils.listFiles(ifwFolder)
        files.forEach {
            val filename = it.split(File.separator).last()
            val content = FileUtils.read(it)
            val file = File(ifwBackupFolder, filename)
            val fileWriter = FileWriter(file)
            fileWriter.write(content)
            fileWriter.close()
        }
        if (Build.VERSION.SDK_INT > 28) FileUtils.getExternalStorageMove(ifwBackupFolder.absolutePath, getBlockerExternalFolder(context, false))
        return files.count()
    }

    fun importIfwRules(context: Context): Int {
        val ifwBackupFolder = getBlockerIFWFolder(context)
        if (Build.VERSION.SDK_INT > 28) FileUtils.getExternalStorageMove(getBlockerExternalFolder(context, false), ifwBackupFolder.absolutePath)
        if (!ifwBackupFolder.exists()) {
            ifwBackupFolder.mkdirs()
            return 0
        }
        val controller = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
        var succeedCount = 0
        ifwBackupFolder.listFiles { file -> file.isFile && file.name.endsWith(".xml") }
                .forEach {
                    val rule = RuleSerializer.deserialize(it) ?: return@forEach
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
                    controller.batchDisable(activities) { _ -> }
                    controller.batchDisable(broadcast) { _ -> }
                    controller.batchDisable(service) { _ -> }
                    succeedCount++
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

    private fun saveRuleToStorage(rule: BlockerRule, dest: File) {
        if (!dest.parentFile.exists()) {
            dest.parentFile.mkdirs()
        }
        if (dest.exists()) {
            dest.delete()
        }
        dest.writeText(GsonBuilder().setPrettyPrinting().create().toJson(rule))
    }

    private fun isApplicationUninstalled(context: Context, savedList: MutableList<String>, packageName: String): Boolean {
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

    // api 29 only, a dirty usage
    @RequiresApi(29)
    @JvmStatic
    private fun getBlockerExternalFolder(context: Context, flag: Boolean): String {
        val path = if (flag) {
            FileUtils.getExternalStoragePath() +
                    PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(context.getString(R.string.key_pref_rule_path), context.getString(R.string.key_pref_rule_path_default_value))
        } else {
            FileUtils.getExternalStoragePath() +
                    PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(context.getString(R.string.key_pref_ifw_rule_path), context.getString(R.string.key_pref_ifw_rule_path_default_value))
        }
        if (!File(path).exists()) {
            File(path).mkdirs()
        }
        return path
    }

    fun getBlockerRuleFolder(context: Context): File {
        val path = FileUtils.getExternalStoragePath(context) +
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(context.getString(R.string.key_pref_rule_path), context.getString(R.string.key_pref_rule_path_default_value))
        if (!File(path).exists()) {
            File(path).mkdirs()
        }
        return File(path)
    }

    private fun getBlockerIFWFolder(context: Context): File {
        val path = FileUtils.getExternalStoragePath(context) +
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(context.getString(R.string.key_pref_ifw_rule_path), context.getString(R.string.key_pref_ifw_rule_path_default_value))
        if (!File(path).exists()) {
            File(path).mkdirs()
        }
        return File(path)
    }

    private fun getController(context: Context): IController {
        val controllerType = PreferenceUtil.getControllerType(context)
        return ComponentControllerProxy.getInstance(controllerType, context)
    }
}
