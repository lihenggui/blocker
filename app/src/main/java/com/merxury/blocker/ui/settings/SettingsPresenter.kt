package com.merxury.blocker.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.merxury.blocker.R
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.entity.Application
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.util.NotificationUtil
import com.merxury.libkit.utils.FileUtils
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader

// TODO Clean Code
class SettingsPresenter(private val context: Context, private val settingsView: SettingsContract.SettingsView) : SettingsContract.SettingsPresenter {

    override fun exportAllRules() {
        var succeedCount = 0
        var failedCount = 0
        var appCount = -1
        Observable.create(ObservableOnSubscribe<Application> { emitter ->
            try {
                val applicationList = ApplicationComponents.getApplicationList(context.packageManager)
                applicationList.forEach {
                    Rule.export(context, it.packageName)
                    succeedCount++
                    emitter.onNext(it)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, e.message)
                failedCount++
                emitter.onError(e)
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    appCount = ApplicationComponents.getApplicationList(context.packageManager).size
                    NotificationUtil.createProcessingNotification(context, appCount)
                }.subscribe({ info ->
                    NotificationUtil.updateProcessingNotification(context, info.label, (succeedCount + failedCount), appCount)
                }, { error ->
                    // onError
                }, {
                    NotificationUtil.finishProcessingNotification(context, succeedCount)
                    settingsView.showExportResult(true, succeedCount, failedCount)
                })
        RxPermissions(context as Activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .map { granted ->
                    if (granted) {
                    } else {
                        settingsView.showMessage(R.string.need_storage_permission)
                    }
                }
    }

    override fun importAllRules() {
        var restoredCount = 0;
        Observable.create(ObservableOnSubscribe<String> { emitter ->
            try {
                val files = FileUtils.listFiles(Rule.getBlockerRuleFolder(context).absolutePath)
                if (files.isEmpty()) {
                    emitter.onComplete()
                    return@ObservableOnSubscribe
                }
                files.filter {
                    it.endsWith(Rule.EXTENSION)
                }.forEach {
                    val rule = Gson().fromJson(FileReader(it), BlockerRule::class.java)
                    if (!ApplicationComponents.isAppInstalled(context.packageManager, rule.packageName)) {
                        return@ObservableOnSubscribe
                    }
                    Rule.import(context, File(it))
                    emitter.onNext(rule.packageName!!)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                Log.e(TAG, e.message)
                e.printStackTrace()
                emitter.onError(e)
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { NotificationUtil.createProcessingNotification(context, 0) }
                .subscribe({ packageName ->
                    restoredCount++
                    NotificationUtil.updateProcessingNotification(context, packageName, 0, 0)
                }, { error ->
                    //onError
                }, {
                    NotificationUtil.finishProcessingNotification(context, restoredCount)
                })
    }

    override fun exportAllIfwRules() {
        val observable = Observable.create(ObservableOnSubscribe<Int> { emitter ->
            val appList = ApplicationComponents.getApplicationList(context.packageManager)
            appList.forEach {
                val packageName = it.packageName
                val file = File(Rule.getBlockerRuleFolder(context), packageName + Rule.EXTENSION)
                if (!file.exists()) {
                    return@forEach
                }
                Rule.import(context, file)
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())

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
        Observable.create(ObservableOnSubscribe<RulesResult> { emitter ->
            val file = File(FileUtils.getExternalStoragePath(), MAT_FILE_NAME)
            if (!file.exists()) {
                emitter.onError(FileNotFoundException("Cannot find MyAndroidTools Rule File: $MAT_FILE_NAME"))
                return@ObservableOnSubscribe
            }
            val result = Rule.importMatRules(context, file)
            emitter.onNext(result)
            emitter.onComplete()
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    // onNext
                }, { error ->
                    //onError
                })
    }

    fun requestStoragePermission() {
        RxPermissions(context as Activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe { result ->

                }
    }

    override fun start(context: Context) {

    }

    override fun destroy() {

    }

    override fun requestPermissions() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPermissionsResult() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        private const val TAG = "SettingsPresenter"
        private const val MAT_FILE_NAME = "myandroidtools.txt"
    }
}