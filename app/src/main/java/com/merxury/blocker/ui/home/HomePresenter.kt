package com.merxury.blocker.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.entity.Application
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class HomePresenter(val pm: PackageManager, val homeView: HomeContract.View) : HomeContract.Presenter {
    init {
        homeView.presenter = this
    }

    @SuppressLint("CheckResult")
    override fun loadApplicationList(context: Context, isSystemApplication: Boolean) {
        homeView.setLoadingIndicator(true)
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
                    homeView.setLoadingIndicator(false)
                    if (applications == null || applications.isEmpty()) {
                        homeView.showNoApplication()
                    } else {
                        homeView.showApplicationList(applications)
                    }
                }
    }

    override fun openApplicationDetails(application: Application) {
        homeView.showApplicationDetailsUi(application)
    }

    override fun result(requestCode: Int, resultCode: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start(context: Context) {

    }

    override fun sortApplicationList(applications: List<Application>): List<Application> {
        return when (currentComparator) {
            ApplicationComparatorType.ASCENDING_BY_LABEL -> applications.sortedBy { it.label }
            ApplicationComparatorType.DESCENDING_BY_LABEL -> applications.sortedByDescending { it.label }
            ApplicationComparatorType.BY_INSTALLATION_DATE -> applications.sortedBy { it.packageName }
        }

    }

    override var currentComparator = ApplicationComparatorType.ASCENDING_BY_LABEL

}