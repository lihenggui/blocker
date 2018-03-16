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

class HomePresenter(val pm: PackageManager, val homeView: HomeContract.View, override var isSystemApplication: Boolean) : HomeContract.Presenter {
    init {
        homeView.presenter = this
    }

    @SuppressLint("CheckResult")
    override fun loadApplicationList(context: Context) {
        homeView.showLoadingProgress()
        Single.create(SingleOnSubscribe<List<Application>> { emitter ->
            val pm = context.packageManager
            val applications: List<Application>
            applications = when (isSystemApplication) {
                false -> ApplicationComponents.getThirdPartyApplicationList(pm)
                true -> ApplicationComponents.getSystemApplicationList(pm)
            }
            emitter.onSuccess(applications)
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { applications ->
                    homeView.hideLoadingProgress()
                    homeView.showApplicationList(applications)
                }

    }

    override fun openApplicationDetails(application: Application) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun result(requestCode: Int, resultCode: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var currentComparator = ApplicationComparatorType.ASCEND_BY_LABEL

}