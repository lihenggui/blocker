package com.merxury.blocker.ui.settings

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonReader
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.ui.component.EComponentType
import com.merxury.blocker.ui.settings.entity.BlockerRule
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader

class SettingsPresenter(private val settingsView: SettingsContract.SettingsView) : SettingsContract.SettingsPresenter {

    private var context: Context? = null

    private val controller: IController by lazy {
        val controllerType = PreferenceUtil.getControllerType(context!!)
        ComponentControllerProxy.getInstance(controllerType, context!!)
    }

    override fun exportAllRules(folder: String) {

    }

    override fun importAllRules(folder: String) {
        val destFolder = File(folder)
        if (!destFolder.exists()) {
            destFolder.mkdirs()
        }
        val rules = destFolder.listFiles { dir -> dir.extension == "json" }
        var successCount = 0
        var failCount = 0
        Observable.create(ObservableOnSubscribe<Unit> { emitter ->
            val gson = Gson()
            rules.forEach {
                try {
                    val jsonReader = JsonReader(FileReader(it))
                    val appRule = gson.fromJson<BlockerRule>(jsonReader, BlockerRule::class.java)
                            ?: return@forEach
                    var ifwController: IntentFirewall? = null
                    // Detects if contains IFW rules, if exists, create a new one.
                    appRule.components.forEach ifwDetection@{
                        if (it.method == EControllerMethod.IFW) {
                            ifwController = IntentFirewallImpl.getInstance(context!!, appRule.packageName)
                            return@ifwDetection
                        }
                    }
                    appRule.components.forEach {
                        when (it.method) {
                            EControllerMethod.IFW -> {
                                when (it.type) {
                                    EComponentType.RECEIVER -> ifwController?.add(it.packageName, it.name, ComponentType.BROADCAST)
                                    EComponentType.SERVICE -> ifwController?.add(it.packageName, it.name, ComponentType.SERVICE)
                                    EComponentType.ACTIVITY -> ifwController?.add(it.packageName, it.name, ComponentType.ACTIVITY)
                                    else -> controller.disable(it.packageName, it.name)
                                }
                            }
                            else -> controller.disable(it.packageName, it.name)
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    Log.e(TAG, ex.message)
                    when (ex) {
                        is FileNotFoundException, is JsonIOException, is JsonSyntaxException -> {
                        }
                    }
                    emitter.onError(ex)
                }
            }
        })
    }

    override fun exportAllIFWRules(folder: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun importAllIFWRules(folder: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun resetIFW() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start(context: Context) {
        this.context = context
    }

    override fun destroy() {
        this.context = null
    }

    companion object {
        const val TAG = "SettingsPresenter"
    }
}