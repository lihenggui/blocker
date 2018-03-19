package com.merxury.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.merxury.core.ApplicationComponents
import com.merxury.entity.Application
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
            emitter.onSuccess(applications)
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { applications ->
                    homeView.setLoadingIndicator(false)
                    if (applications.isEmpty()) {
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

    override var currentComparator = ApplicationComparatorType.ASCEND_BY_LABEL

}