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
import com.merxury.blocker.core.root.RootCommand
import com.merxury.blocker.entity.Application
import com.merxury.blocker.utils.FileUtils
import com.merxury.ifw.util.PermissionUtils
import com.merxury.ifw.util.StorageUtils
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
        //TODO dirty code, refine later
        RxPermissions(context as Activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        Single.create(SingleOnSubscribe<Boolean> { emitter ->
                            if (!FileUtils.isExternalStorageWritable()) {
                                emitter.onSuccess(false)
                            }
                            val ifwFolder = StorageUtils.getIfwFolder();
                            val blockerFolder = FileUtils.getExternalStoragePath() + BLOCKER_FOLDER;
                            val blockerFolderFile = File(blockerFolder)
                            if(!blockerFolderFile.exists()) {
                                blockerFolderFile.mkdirs()
                            }
                            try {
                                PermissionUtils.setIfwReadable()
                                val files = File(ifwFolder).listFiles()
                                if(files == null) emitter.onSuccess(false)
                                files.forEach {
                                    FileUtils.cat(it.absolutePath, blockerFolder + it.name)
                                }
                                PermissionUtils.resetIfwPermission()
                            }catch (e: IOException) {
                                e.printStackTrace()
                                Log.e(TAG, e.message)
                                emitter.onSuccess(false)
                            }
                            emitter.onSuccess(true)
                        }).subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { result ->
                                    if (result) {
                                        homeView?.showToastMessage(context?.getString(R.string.export_rule_succeed, BLOCKER_FOLDER), Toast.LENGTH_LONG)
                                    } else {
                                        homeView?.showToastMessage(context?.getString(R.string.export_rule_failed), Toast.LENGTH_LONG)
                                    }
                                }
                    } else {
                        homeView?.showToastMessage(context?.getString(R.string.export_rule_failed), Toast.LENGTH_LONG)
                    }
                }

    }

    override fun importIfwRules() {
        //TODO dirty code, refine later
        RxPermissions(context as Activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        Single.create(SingleOnSubscribe<Boolean> { emitter ->
                            if (!FileUtils.isExternalStorageWritable()) {
                                emitter.onSuccess(false)
                            }
                            val ifwFolder = StorageUtils.getIfwFolder();
                            val blockerFolder = FileUtils.getExternalStoragePath() + BLOCKER_FOLDER;
                            try {
                                PermissionUtils.setIfwReadable()
                                val files = File(ifwFolder).listFiles()
                                if(files == null) emitter.onSuccess(false)
                                files.forEach {
                                    FileUtils.copy(it.absolutePath, blockerFolder + it.name)
                                }
                                PermissionUtils.resetIfwPermission()
                            }catch (e: IOException) {
                                e.printStackTrace()
                                Log.e(TAG, e.message)
                                emitter.onSuccess(false)
                            }
                            emitter.onSuccess(true)
                        }).subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { result ->
                                    if (result) {
                                        homeView?.showToastMessage(context?.getString(R.string.export_rule_succeed, BLOCKER_FOLDER), Toast.LENGTH_LONG)
                                    } else {
                                        homeView?.showToastMessage(context?.getString(R.string.export_rule_failed), Toast.LENGTH_LONG)
                                    }
                                }
                    } else {
                        homeView?.showToastMessage(context?.getString(R.string.export_rule_failed), Toast.LENGTH_LONG)
                    }
                }

    }


    override var currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL

    companion object {
        const val TAG = "HomePresenter"
        const val BLOCKER_FOLDER = "Blocker/ifw/"
    }

}