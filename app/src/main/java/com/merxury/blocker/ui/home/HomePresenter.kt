package com.merxury.blocker.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.AppLauncher
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.ETrimMemoryLevel
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ManagerUtils
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class HomePresenter(var homeView: HomeContract.View?) : HomeContract.Presenter {
    private var context: Context? = null
    private val logger = XLog.tag(this.javaClass.simpleName).build()

    override fun start(context: Context) {
        this.context = context
        homeView?.presenter = this
    }

    override fun destroy() {
        context = null
        homeView = null
    }

    @SuppressLint("CheckResult")
    override fun loadApplicationList(context: Context, isSystemApplication: Boolean) {
        homeView?.setLoadingIndicator(true)
        Single.create(SingleOnSubscribe<MutableList<Application>> { emitter ->
            val applications: MutableList<Application> = when (isSystemApplication) {
                false -> ApplicationUtil.getThirdPartyApplicationList(context)
                true -> ApplicationUtil.getSystemApplicationList(context)
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

    override fun sortApplicationList(applications: List<Application>): MutableList<Application> {
        val sortedList = when (currentComparator) {
            ApplicationComparatorType.ASCENDING_BY_LABEL -> applications.asSequence().sortedBy { it.label }
            ApplicationComparatorType.DESCENDING_BY_LABEL -> applications.asSequence().sortedByDescending { it.label }
            ApplicationComparatorType.INSTALLATION_TIME -> applications.asSequence().sortedByDescending { it.firstInstallTime }
            ApplicationComparatorType.LAST_UPDATE_TIME -> applications.asSequence().sortedByDescending { it.lastUpdateTime }
        }
        return sortedList.asSequence().sortedWith(compareBy({ !it.isBlocked }, { !it.isEnabled })).toMutableList()
    }

    override fun launchApplication(packageName: String) {
        context?.let {
            AppLauncher.startApplication(it, packageName)
        }
    }

    @SuppressLint("CheckResult")
    override fun forceStop(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.forceStop(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->

                }, {

                })
    }

    @SuppressLint("CheckResult")
    override fun enableApplication(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.enableApplication(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result) {
                        homeView?.updateState(packageName)
                    }
                }, {
                    logger.e(it)
                })
    }

    @SuppressLint("CheckResult")
    override fun disableApplication(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.disableApplication(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result) {
                        homeView?.updateState(packageName)
                    }
                }, {
                    logger.e(it)
                })
    }

    @SuppressLint("CheckResult")
    override fun clearData(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.clearData(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result) {
                        homeView?.showDataCleared()
                    }
                }, {
                    logger.e(it)
                })
    }

    @SuppressLint("CheckResult")
    override fun trimMemory(packageName: String, level: ETrimMemoryLevel) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.trimMemory(packageName, level)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })
    }

    override fun showDetails(packageName: String) {
        context?.let {
            AppLauncher.showApplicationDetails(it, packageName)
        }
    }

    override fun blockApplication(packageName: String) {
        ApplicationUtil.addBlockedApplication(context!!, packageName)
        homeView?.updateState(packageName)
    }

    override fun unblockApplication(packageName: String) {
        ApplicationUtil.removeBlockedApplication(context!!, packageName)
        homeView?.updateState(packageName)
    }

    override var currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL
        set(comparator) {
            field = comparator
            context?.let {
                val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
                editor.putInt(it.getString(R.string.key_pref_comparator_type), comparator.value)
                editor.apply()
            }
        }
        get() {
            context?.let {
                val pref = PreferenceManager.getDefaultSharedPreferences(context)
                val comparatorType = pref.getInt(it.getString(R.string.key_pref_comparator_type), 0)
                return ApplicationComparatorType.from(comparatorType)
            }
            return ApplicationComparatorType.DESCENDING_BY_LABEL
        }
}