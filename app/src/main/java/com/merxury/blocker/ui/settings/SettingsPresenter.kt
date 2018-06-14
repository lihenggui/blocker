package com.merxury.blocker.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.util.PreferenceUtil
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import java.io.File

class SettingsPresenter(private val settingsView: SettingsContract.SettingsView) : SettingsContract.SettingsPresenter {

    private var context: Context? = null

    private val controller: IController by lazy {
        val controllerType = PreferenceUtil.getControllerType(context!!)
        ComponentControllerProxy.getInstance(controllerType, context!!)
    }

    override fun exportAllRules(folder: String) {
        RxPermissions(context as Activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe({ granted ->
                    if (granted) {
                        exportBlockerRule(folder)
                    } else {

                    }
                })
    }

    override fun importAllRules(folder: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

    private fun exportBlockerRule(folder: String) {
        val destFolder = File(folder)
        if (!destFolder.exists()) {
            destFolder.mkdirs()
        }
        val rules = destFolder.listFiles { dir -> dir.extension == "json" }
        var successCount = 0
        var failCount = 0
        Observable.create(ObservableOnSubscribe<Unit> { emitter ->
            val gson = Gson()
        })
    }

    override fun start(context: Context) {
        this.context = context
    }

    override fun destroy() {
        this.context = null
    }

}