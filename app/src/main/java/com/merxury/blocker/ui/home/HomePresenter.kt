package com.merxury.blocker.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import com.merxury.blocker.R
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.entity.Application
import com.merxury.blocker.exception.StorageNotAvailableException
import com.merxury.libkit.utils.FileUtils
import com.merxury.libkit.utils.StorageUtils
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.IOException

class HomePresenter(var homeView: HomeContract.View?) : HomeContract.Presenter {

    private lateinit var pm: PackageManager
    private var context: Context? = null

    @SuppressLint("CheckResult")
    override fun loadApplicationList(context: Context, isSystemApplication: Boolean) {
        homeView?.setLoadingIndicator(true)
        Single.create(SingleOnSubscribe<List<Application>> { emitter ->
            val applications: List<Application> = when (isSystemApplication) {
                false -> ApplicationComponents.getThirdPartyApplicationList(pm)
                true -> ApplicationComponents.getSystemApplicationList(pm)
            }
            val sortedList = sortApplicationList(applications)
            emitter.onSuccess(sortedList)
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { applications ->
                    homeView?.setLoadingIndicator(false)
                    if (applications == null || applications.isEmpty()) {
                        homeView?.showNoApplication()
                    } else {
                        homeView?.showApplicationList(applications)
                    }
                }
    }

    override fun openApplicationDetails(application: Application) {
        homeView?.showApplicationDetailsUi(application)
    }

    override fun result(requestCode: Int, resultCode: Int) {

    }

    override fun start(context: Context) {
        this.context = context
        pm = context.packageManager
        homeView?.presenter = this
    }

    override fun destroy() {
        context = null
        homeView = null
    }

    override fun sortApplicationList(applications: List<Application>): List<Application> {
        return when (currentComparator) {
            ApplicationComparatorType.ASCENDING_BY_LABEL -> applications.sortedBy { it.label }.toMutableList()
            ApplicationComparatorType.DESCENDING_BY_LABEL -> applications.sortedByDescending { it.label }.toMutableList()
            ApplicationComparatorType.BY_INSTALLATION_DATE -> applications.sortedBy { it.packageName }.toMutableList()
        }
    }

    override fun exportIfwRules() {
        RxPermissions(context as Activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        exportRule()
                    } else {
                        homeView?.showToastMessage(context?.getString(R.string.export_rule_failed_no_permission), Toast.LENGTH_LONG)
                    }
                }
    }

    override fun importIfwRules() {
        RxPermissions(context as Activity)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        importRule()
                    } else {
                        homeView?.showToastMessage(context?.getString(R.string.import_rule_failed_no_permission), Toast.LENGTH_LONG)
                    }
                }
    }

    private fun exportRule() {
        //TODO dirty code, refine later
        Single.create(SingleOnSubscribe<Int> { emitter ->
            if (!FileUtils.isExternalStorageWritable()) {
                emitter.onError(StorageNotAvailableException())
            }
            val ifwFolder = StorageUtils.getIfwFolder();
            val blockerFolder = FileUtils.getExternalStoragePath() + BLOCKER_FOLDER;
            val blockerFolderFile = File(blockerFolder)
            if (!blockerFolderFile.exists()) {
                blockerFolderFile.mkdirs()
            }
            try {
                val files = FileUtils.listFiles(ifwFolder)
                files.forEach {
                    FileUtils.cat(it, blockerFolder + it.split(File.separator).last())
                }
                emitter.onSuccess(files.count())
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, e.message)
                emitter.onError(e)
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    homeView?.showToastMessage(context?.resources?.getQuantityString(R.plurals.export_rule_succeed, result, result, BLOCKER_FOLDER), Toast.LENGTH_LONG)
                }, { error ->
                    homeView?.showToastMessage(context?.getString(R.string.export_rule_failed_exception, error.message), Toast.LENGTH_LONG)
                })
    }

    private fun importRule() {
        //TODO dirty code, refine later
        Single.create(SingleOnSubscribe<Int> { emitter ->
            if (!FileUtils.isExternalStorageWritable()) {
                emitter.onError(StorageNotAvailableException())
            }
            val ifwFolder = StorageUtils.getIfwFolder();
            val blockerFolder = FileUtils.getExternalStoragePath() + BLOCKER_FOLDER;
            val blockerFolderFile = File(blockerFolder)
            if (!blockerFolderFile.exists()) {
                blockerFolderFile.mkdirs()
            }
            try {
                val files = FileUtils.listFiles(blockerFolder)
                files.forEach {
                    FileUtils.cat(it, ifwFolder + it.split(File.separator).last())
                }
                emitter.onSuccess(files.count())
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, e.message)
                emitter.onError(e)
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    homeView?.showToastMessage(context?.resources?.getQuantityString(R.plurals.import_rule_succeed, result, result), Toast.LENGTH_LONG)
                }, { error ->
                    homeView?.showToastMessage(context?.getString(R.string.import_rule_failed_exception, error.message), Toast.LENGTH_LONG)
                })
    }

    override fun requestPermissions() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPermissionsResult() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override var currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL

    companion object {
        const val TAG = "HomePresenter"
        const val BLOCKER_FOLDER = "Blocker/ifw/"
    }

}