package com.merxury.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.util.Log
import com.merxury.core.ApplicationComponents
import com.merxury.core.IController
import com.merxury.core.root.ComponentControllerProxy
import com.merxury.core.root.EControllerMethod
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ComponentPresenter(val pm: PackageManager, val view: ComponentContract.View) : ComponentContract.Presenter, IController {

    private val controller: IController by lazy {
        ComponentControllerProxy.getInstance(EControllerMethod.PM, null)
    }

    init {
        view.presenter = this
    }

    @SuppressLint("CheckResult")
    override fun loadComponents(pm: PackageManager, packageName: String, type: EComponentType) {
        view.setLoadingIndicator(true)
        Single.create((SingleOnSubscribe<List<ComponentInfo>> { emitter ->
            val componentList = when (type) {
                EComponentType.RECEIVER -> ApplicationComponents.getReceiverList(pm, packageName)
                EComponentType.ACTIVITY -> ApplicationComponents.getActivityList(pm, packageName)
                EComponentType.SERVICE -> ApplicationComponents.getServiceList(pm, packageName)
                EComponentType.PROVIDER -> ApplicationComponents.getProviderList(pm, packageName)
            }
            emitter.onSuccess(componentList)
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ components ->
                    view.setLoadingIndicator(false)
                    if (components.isEmpty()) {
                        view.showNoComponent()
                    } else {
                        view.showComponentList(components)
                    }
                })
    }

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            emitter.onSuccess(controller.switchComponent(packageName, componentName, state))
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, { error: Throwable? ->
                    Log.e(TAG, error?.message);
                })
        return true
    }

    override fun enableComponent(componentInfo: ComponentInfo): Boolean {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            emitter.onSuccess(controller.enableComponent(componentInfo))
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
        return true
    }

    override fun disableComponent(componentInfo: ComponentInfo): Boolean {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            emitter.onSuccess(controller.disableComponent(componentInfo))
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
        return true
    }


    override fun start(context: Context) {
    }

    companion object {
        const val TAG = "ComponentPresenter"
    }
}