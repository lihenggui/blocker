package com.merxury.blocker.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.R
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.FileUtils
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.doAsync
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader

// TODO Clean Code
class SettingsPresenter(
    private val context: Context,
    private val settingsView: SettingsContract.SettingsView
) : SettingsContract.SettingsPresenter {
    private val logger = XLog.tag("SettingsPresenter").build()
    private val exceptionHandler = { e: Throwable -> logger.e(e) }

    override fun exportAllRules() {
        var succeedCount = 0
        var failedCount = 0
        var appCount = 0
        val errorHandler = { e: Throwable ->
            failedCount++
            logger.e(e)
        }
        doAsync(errorHandler) {
            val applicationList = ApplicationUtil.getApplicationList(context)
            appCount = applicationList.size
            NotificationUtil.createProcessingNotification(context, appCount)
            applicationList.forEach { currentApp ->
                Rule.export(context, currentApp.packageName)
                succeedCount++
                NotificationUtil.updateProcessingNotification(
                    context,
                    currentApp.label,
                    (succeedCount + failedCount),
                    appCount
                )
            }
            Thread.sleep(1000L)
            NotificationUtil.finishProcessingNotification(context, succeedCount)
            settingsView.showExportResult(true, succeedCount, failedCount)
        }
    }

    override fun importAllRules() {
        var restoredCount = 0
        var rulesCount = 0
        val importObservable = Observable.create(ObservableOnSubscribe<String> { emitter ->
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
                    if (!ApplicationUtil.isAppInstalled(context.packageManager, rule.packageName)) {
                        return@forEach
                    }
                    Rule.import(context, File(it))
                    emitter.onNext(rule.packageName!!)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                logger.e("Error occurs in importing rules:", e)
                emitter.onError(e)
            }
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                rulesCount = FileUtils.getFileCounts(
                    Rule.getBlockerRuleFolder(context).absolutePath,
                    Rule.EXTENSION
                )
                NotificationUtil.createProcessingNotification(context, rulesCount)
            }
        RxPermissions(context as Activity)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
            .map { granted ->
                if (granted) {
                    importObservable.subscribe({ packageName ->
                        restoredCount++
                        NotificationUtil.updateProcessingNotification(
                            context,
                            packageName,
                            restoredCount,
                            rulesCount
                        )
                    }, { error ->
                        //onError
                    }, {
                        NotificationUtil.finishProcessingNotification(context, restoredCount)
                    })
                } else {
                    settingsView.showMessage(R.string.need_storage_permission)
                }
            }
            .subscribe()
    }

    override fun exportAllIfwRules() {
        var exportedCount = 0
        val exportIfwObservable = Observable.create(ObservableOnSubscribe<Int> { emitter ->
            try {
                exportedCount = Rule.exportIfwRules(context)
            } catch (e: Exception) {
                logger.e(e.message)
                e.printStackTrace()
            }
            emitter.onComplete()
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { NotificationUtil.createProcessingNotification(context, 0) }
        RxPermissions(context as Activity)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
            .map { granted ->
                if (granted) {
                    exportIfwObservable.subscribe({ _ ->

                    }, { error ->
                        //onError
                    }, {
                        NotificationUtil.finishProcessingNotification(context, exportedCount)
                    })
                } else {
                    settingsView.showMessage(R.string.need_storage_permission)
                }
            }
            .subscribe()
    }

    override fun importAllIfwRules() {
        var count = 0
        val importIfwObservable = Observable.create(ObservableOnSubscribe<Int> { emitter ->
            try {
                count = Rule.importIfwRules(context)
                emitter.onComplete()
            } catch (e: Exception) {
                logger.e("Error while importing:", e)
                emitter.onError(e)
            }
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { NotificationUtil.createProcessingNotification(context, 0) }

        RxPermissions(context as Activity)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
            .map { granted ->
                if (granted) {
                    importIfwObservable.subscribe({ _ ->
                        //onNext
                    }, { error ->
                        //onError
                    }, {
                        NotificationUtil.finishProcessingNotification(context, count)
                        settingsView.showExportResult(true, count, 0)
                    })
                }
            }
            .subscribe()
    }

    @SuppressLint("CheckResult")
    override fun resetIFW() {
        Observable.create(ObservableOnSubscribe<Boolean> { emitter ->
            val result = Rule.resetIfw()
            emitter.onNext(result)
            emitter.onComplete()
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result) {
                    settingsView.showMessage(R.string.done)
                } else {
                    settingsView.showMessage(R.string.ifw_reset_error)
                }

            }, { error ->
                settingsView.showMessage(R.string.ifw_reset_error)
            })
    }

    override fun importMatRules(filePath: String?) {
        val importMatSingle = Single.create(SingleOnSubscribe<RulesResult> { emitter ->
            try {
                if (filePath == null) {
                    emitter.onError(NullPointerException("File path cannot be null"))
                    return@SingleOnSubscribe
                }
                val file = File(filePath)
                if (!file.exists()) {
                    emitter.onError(FileNotFoundException("Cannot find MyAndroidTools Rule File: ${file.path}"))
                    return@SingleOnSubscribe
                }
                val result = Rule.importMatRules(context, file) { context, name, current, total ->
                    NotificationUtil.updateProcessingNotification(context, name, current, total)
                }
                // TODO: Temporary fix to the notification update limit in Android
                Thread.sleep(1000)
                emitter.onSuccess(result)
            } catch (e: Exception) {
                logger.e("Error occurs in importing mat rules:", e)
                emitter.onError(e)
            }
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { NotificationUtil.createProcessingNotification(context, 0) }
        RxPermissions(context as Activity)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
            .map { granted ->
                if (granted) {
                    importMatSingle.subscribe({ result ->
                        NotificationUtil.finishProcessingNotification(
                            context,
                            result.failedCount + result.succeedCount
                        )
                    }, { error ->
                        NotificationUtil.finishProcessingNotification(context, 0)
                        ToastUtil.showToast(error.message ?: error.toString())
                    })
                }
            }
            .subscribe()
    }

    override fun start(context: Context) {

    }

    override fun destroy() {

    }
}