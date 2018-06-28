package com.merxury.blocker.ui.settings

import android.content.Context
import android.util.Log
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.rule.Rule
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

// TODO Clean Code
class SettingsPresenter(private val context: Context, private val settingsView: SettingsContract.SettingsView) : SettingsContract.SettingsPresenter {

    override fun exportAllRules() {
        var succeedCount = 0
        var failedCount = 0
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
                    succeedCount = count
                    // onNext
                }, { error ->
                    // onError
                }, {
                    settingsView.showExportResult(true, succeedCount, failedCount)
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

    override fun exportAllIfwRules() {
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

    override fun importAllIfwRules() {
        var count = 0
        Observable.create(ObservableOnSubscribe<Int> { emitter ->
            try {
                count = Rule.exportIfwRules(context)
                emitter.onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, e.message)
                emitter.onError(e)
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                    //onNext
                }, { error ->
                    //onError
                }, {
                    settingsView.showExportResult(true, count, 0)
                })
    }

    override fun resetIFW() {
        Observable.create(ObservableOnSubscribe<Boolean> { emitter ->
            val result = Rule.resetIfw()
            emitter.onNext(result)
            emitter.onComplete()
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    settingsView.showResetResult(result)
                }, { error ->
                    //onError
                })
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