package com.merxury.blocker.rule

import android.content.ComponentName
import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.merxury.blocker.R
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.rule.entity.ComponentRule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.ui.component.EComponentType
import com.merxury.blocker.ui.settings.SettingsPresenter
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.utils.FileUtils
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import java.io.File
import java.io.FileReader

object Rule {
    const val EXTENSION = ".json"
    const val TAG = "Rule"

    fun export(context: Context, packageName: String): RulesResult {
        Log.i(SettingsPresenter.TAG, "Backup rules for ${packageName}")
        val pm = context.packageManager
        val applicationInfo = ApplicationComponents.getApplicationComponents(pm, packageName)
        val rule = BlockerRule(packageName = applicationInfo.packageName, versionName = applicationInfo.versionName, versionCode = applicationInfo.versionCode)
        var disabledComponentsCount = 0;
        applicationInfo.receivers?.forEach {
            if (!ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))) {
                rule.components.add(ComponentRule(it.packageName, it.name, EComponentType.RECEIVER))
                disabledComponentsCount++
            }
        }
        applicationInfo.services?.forEach {
            if (!ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))) {
                rule.components.add(ComponentRule(it.packageName, it.name, EComponentType.SERVICE))
                disabledComponentsCount++
            }
        }
        applicationInfo.activities?.forEach {
            if (!ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))) {
                rule.components.add(ComponentRule(it.packageName, it.name, EComponentType.ACTIVITY))
                disabledComponentsCount++
            }
        }
        applicationInfo.providers?.forEach {
            if (!ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(it.packageName, it.name))) {
                rule.components.add(ComponentRule(it.packageName, it.name, EComponentType.RECEIVER))
                disabledComponentsCount++
            }
        }
        return if (rule.components.isNotEmpty()) {
            saveRuleToStorage(rule, File(getBlockerFolder(context), packageName + EXTENSION))
            RulesResult(true, disabledComponentsCount, 0)
        } else {
            RulesResult(false, 0, 0)
        }
    }

    fun import(context: Context, file: File): RulesResult {
        var succeedCount = 0
        var failedCount = 0
        val jsonReader = JsonReader(FileReader(file))
        val appRule = Gson().fromJson<BlockerRule>(jsonReader, BlockerRule::class.java)
                ?: return RulesResult(false, 0, 0)
        val controller = getController(context)
        var ifwController: IntentFirewall? = null
        // Detects if contains IFW rules, if exists, create a new one.
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
                            EComponentType.RECEIVER -> ifwController?.add(it.packageName, it.name, ComponentType.BROADCAST)
                                    ?: false
                            EComponentType.SERVICE -> ifwController?.add(it.packageName, it.name, ComponentType.SERVICE)
                                    ?: false
                            EComponentType.ACTIVITY -> ifwController?.add(it.packageName, it.name, ComponentType.ACTIVITY)
                                    ?: false
                            else -> controller.disable(it.packageName, it.name)
                        }
                    }
                    else -> controller.disable(it.packageName, it.name)
                }
                if (controllerResult) {
                    succeedCount++
                } else {
                    failedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, e.message)
            return RulesResult(false, succeedCount, failedCount)
        }
        return RulesResult(true, succeedCount, failedCount)
    }

    private fun saveRuleToStorage(rule: BlockerRule, dest: File) {
        if (dest.exists()) {
            dest.delete()
        }
        dest.writeText(Gson().toJson(rule))
    }

    private fun getBlockerFolder(context: Context): File {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val path = pref.getString(context.getString(R.string.key_pref_rule_path), context.getString(R.string.key_pref_rule_path_default_value))
        val storagePath = FileUtils.getExternalStoragePath();
        return File(storagePath, path)
    }

    private fun getController(context: Context): IController {
        val controllerType = PreferenceUtil.getControllerType(context)
        return ComponentControllerProxy.getInstance(controllerType, context)
    }
}