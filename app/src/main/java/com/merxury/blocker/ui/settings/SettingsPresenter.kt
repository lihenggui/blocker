package com.merxury.blocker.ui.settings

import android.content.Context
import android.util.Log
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.util.PreferenceUtil
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

// TODO Clean Code
class SettingsPresenter(private val context: Context, private val settingsView: SettingsContract.SettingsView) : SettingsContract.SettingsPresenter {

    private val controller: IController by lazy {
        val controllerType = PreferenceUtil.getControllerType(context)
        ComponentControllerProxy.getInstance(controllerType, context)
    }

    override fun exportAllRules() {
        Observable.create(ObservableOnSubscribe<Int> { emitter ->
            try {
                val applicationList = ApplicationComponents.getApplicationList(context.packageManager)
                var count = 0
                applicationList.forEach {
                    Rule.export(context, it.packageName)
                    emitter.onNext(count++)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, e.message)
                emitter.onError(e)
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ count ->
                    //onNext
                }, { error ->
                    //onError
                }, {
                    //onComplete
                })
    }

    override fun importAllRules() {
        Observable.create(ObservableOnSubscribe<Int> { emitter ->
            val appList = ApplicationComponents.getApplicationList(context.packageManager)
            appList.forEach {
                val packageName = it.packageName
                val file = File(Rule.getBlockerRuleFolder(context), packageName + Rule.EXTENSION)
                if (!file.exists()) {
                    return@forEach
                }
                Rule.import(context, file) { _, _, _, _ -> }
            }
        })
    }

    override fun exportAllIfwRules(folder: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun importAllIfwRules(folder: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun resetIFW() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun importMatRules() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start(context: Context) {

    }

    override fun destroy() {

    }

    companion object {
        const val TAG = "SettingsPresenter"
    }
}