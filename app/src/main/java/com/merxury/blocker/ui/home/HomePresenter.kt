package com.merxury.blocker.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.preference.PreferenceManager
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

    private lateinit var pm: PackageManager
    private var context: Context? = null

    override fun start(context: Context) {
        this.context = context
        pm = context.packageManager
        homeView?.presenter = this
    }

    override fun destroy() {
        context = null
        homeView = null
    }

    @SuppressLint("CheckResult")
    override fun loadApplicationList(context: Context, isSystemApplication: Boolean) {
        homeView?.setLoadingIndicator(true)
        Single.create(SingleOnSubscribe<List<Application>> { emitter ->
            val applications: List<Application> = when (isSystemApplication) {
                false -> ApplicationUtil.getThirdPartyApplicationList(pm)
                true -> ApplicationUtil.getSystemApplicationList(pm)
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

    override fun sortApplicationList(applications: List<Application>): List<Application> {
        return when (currentComparator) {
            ApplicationComparatorType.ASCENDING_BY_LABEL -> applications.sortedBy { it.label }.toMutableList()
            ApplicationComparatorType.DESCENDING_BY_LABEL -> applications.sortedByDescending { it.label }.toMutableList()
            ApplicationComparatorType.INSTALLATION_TIME -> applications.sortedByDescending { it.firstInstallTime }.toMutableList()
            ApplicationComparatorType.LAST_UPDATE_TIME -> applications.sortedByDescending { it.lastUpdateTime }.toMutableList()
        }
    }

    override fun launchApplication(packageName: String) {
        context?.let {
            AppLauncher.startApplication(it, packageName)
        }
    }

    override fun forceStop(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.forceStop(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })
    }

    override fun enableApplication(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.enableApplication(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })
    }

    override fun disableApplication(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.disableApplication(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })
    }

    override fun clearData(packageName: String) {
        Single.create(SingleOnSubscribe<Boolean> { emitter ->
            ManagerUtils.clearData(packageName)
            emitter.onSuccess(true)
        })
                .onErrorReturn { false }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })
    }

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