package com.merxury.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.merxury.core.ApplicationComponents
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ComponentPresenter(val pm: PackageManager, val view: ComponentContract.View) : ComponentContract.Presenter {

    init {
        view.presenter = this
    }

    @SuppressLint("CheckResult")
    override fun loadComponents(pm: PackageManager, packageName: String, type: EComponentType) {
        view.setLoadingIndicator(true)
        var componentList: List<ComponentInfo>
        Single.create((SingleOnSubscribe<List<ComponentInfo>> { emitter ->
            componentList = when (type) {
                EComponentType.RECEIVER -> ApplicationComponents.getReceiverList(pm, packageName)
                EComponentType.ACTIVITY -> ApplicationComponents.getActivityList(pm, packageName)
                EComponentType.SERVICE -> ApplicationComponents.getServiceList(pm, packageName)
                EComponentType.PROVIDER -> ApplicationComponents.getProviderList(pm, packageName)
            }
            emitter.onSuccess(componentList)
        })).observeOn(Schedulers.io())
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({ components ->
                    view.setLoadingIndicator(false)
                    if (components.isEmpty()) {
                        view.showNoComponent()
                    } else {
                        view.showComponentList(components)
                    }
                })
    }


    override fun start(context: Context) {
    }

}