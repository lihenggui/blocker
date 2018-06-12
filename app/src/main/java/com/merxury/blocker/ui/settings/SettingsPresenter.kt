package com.merxury.blocker.ui.settings

import android.content.Context
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.util.PreferenceUtil

class SettingsPresenter : SettingsContract.SettingsPresenter {

    private var context: Context? = null

    private val controller: IController by lazy {
        val controllerType = PreferenceUtil.getControllerType(context!!)
        ComponentControllerProxy.getInstance(controllerType, context!!)
    }

    override fun exportAllRules(folder: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

    override fun start(context: Context) {
        this.context = context
    }

    override fun destroy() {
        this.context = null
    }

}