package com.merxury.blocker.ui.settings

import android.content.Context

class SettingsPresenter : SettingsContract.SettingsPresenter {

    private var context: Context? = null

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