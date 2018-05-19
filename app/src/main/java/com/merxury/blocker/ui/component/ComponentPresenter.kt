package com.merxury.blocker.ui.component

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.util.Log
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.ComponentControllerProxy
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.root.RootCommand
import com.merxury.blocker.entity.getSimpleName
import com.merxury.blocker.ui.strategy.entity.view.ComponentBriefInfo
import com.merxury.blocker.ui.strategy.service.ApiClient
import com.merxury.blocker.ui.strategy.service.IClientServer
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiConsumer
import io.reactivex.schedulers.Schedulers

class ComponentPresenter(val context: Context, val view: ComponentContract.View, val packageName: String) : ComponentContract.Presenter, IController {

    private val pm: PackageManager

    private val controller: IController by lazy {
        ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
    }
    private val componentClient: IClientServer by lazy {
        ApiClient.createClient()
    }

    private val ifwController: IntentFirewall by lazy {
        IntentFirewallImpl(context, packageName)
    }

    init {
        view.presenter = this
        pm = context.packageManager
    }

    @SuppressLint("CheckResult")
    override fun loadComponents(packageName: String, type: EComponentType) {
        Log.i(TAG, "Load components for $packageName, type: $type")
        view.setLoadingIndicator(true)
        Single.create((SingleOnSubscribe<List<ComponentInfo>> { emitter ->
            var componentList = when (type) {
                EComponentType.RECEIVER -> ApplicationComponents.getReceiverList(pm, packageName)
                EComponentType.ACTIVITY -> ApplicationComponents.getActivityList(pm, packageName)
                EComponentType.SERVICE -> ApplicationComponents.getServiceList(pm, packageName)
                EComponentType.PROVIDER -> ApplicationComponents.getProviderList(pm, packageName)
                else -> ArrayList<ComponentInfo>()
            }
            componentList = sortComponentList(componentList, currentComparator)
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

    @SuppressLint("CheckResult")
    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                val result = controller.switchComponent(packageName, componentName, state)
                emitter.onSuccess(result)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(BiConsumer { _, error ->
                    view.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view.showAlertDialog()
                    }
                })
        return true
    }

    @SuppressLint("CheckResult")
    override fun enable(packageName: String, componentName: String): Boolean {
        Log.i(TAG, "Enable component: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                val result = controller.enable(packageName, componentName)
                emitter.onSuccess(result)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(BiConsumer { _, error ->
                    view.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view.showAlertDialog()
                    }
                })
        return true
    }

    @SuppressLint("CheckResult")
    override fun disable(packageName: String, componentName: String): Boolean {
        Log.i(TAG, "Disable component: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                val result = controller.disable(packageName, componentName)
                emitter.onSuccess(result)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(BiConsumer { _, error ->
                    view.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view.showAlertDialog()
                    }
                })
        return true
    }

    override fun sortComponentList(components: List<ComponentInfo>, type: EComponentComparatorType): List<ComponentInfo> {
        return when (type) {
            EComponentComparatorType.NAME_ASCENDING -> components.sortedBy { it.getSimpleName() }
            EComponentComparatorType.NAME_DESCENDING -> components.sortedByDescending { it.getSimpleName() }
            EComponentComparatorType.PACKAGE_NAME_ASCENDING -> components.sortedBy { it.name }
            EComponentComparatorType.PACKAGE_NAME_DESCENDING -> components.sortedByDescending { it.name }
        }
    }

    override fun checkComponentIsVoted(component: ComponentInfo): Boolean {
        val sharedPreferences = context.getSharedPreferences(component.packageName, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(component.name, false)

    }

    override fun writeComponentVoteState(component: ComponentInfo, like: Boolean) {
        val sharedPreferences = context.getSharedPreferences(component.packageName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(component.name, like)
        editor.apply()
    }

    @SuppressLint("CheckResult")
    override fun voteForComponent(component: ComponentInfo, type: EComponentType) {
        componentClient.upVoteForComponent(ComponentBriefInfo(component.packageName, component.name, type))
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    writeComponentVoteState(component, true)
                    view.refreshComponentState(component.name)
                }, { error ->

                })
    }

    @SuppressLint("CheckResult")
    override fun downVoteForComponent(component: ComponentInfo, type: EComponentType) {
        componentClient.downVoteForComponent(ComponentBriefInfo(component.packageName, component.name, type))
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    writeComponentVoteState(component, false)
                    view.refreshComponentState(component.name)
                }, { error ->

                })
    }

    override fun addToIFW(packageName: String, componentName: String, type: EComponentType) {
        Log.i(TAG, "Disable component via IFW: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                when (type) {
                    EComponentType.ACTIVITY -> ifwController.add(packageName, componentName, ComponentType.ACTIVITY)
                    EComponentType.RECEIVER -> ifwController.add(packageName, componentName, ComponentType.BROADCAST)
                    EComponentType.SERVICE -> ifwController.add(packageName, componentName, ComponentType.SERVICE)
                    else -> {
                    }
                }
                ifwController.save()
                emitter.onSuccess(true)
                //TODO Duplicated code
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                    view.refreshComponentState(componentName)
                }, { error ->
                    error?.apply {
                        ifwController.reload()
                        Log.e(TAG, message)
                        printStackTrace()
                        view.showAlertDialog()
                    }
                })
    }

    override fun removeFromIFW(packageName: String, componentName: String, type: EComponentType) {
        Log.i(TAG, "Disable component via IFW: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                when (type) {
                    EComponentType.ACTIVITY -> ifwController.remove(packageName, componentName, ComponentType.ACTIVITY)
                    EComponentType.RECEIVER -> ifwController.remove(packageName, componentName, ComponentType.BROADCAST)
                    EComponentType.SERVICE -> ifwController.remove(packageName, componentName, ComponentType.SERVICE)
                    else -> {
                    }
                }
                ifwController.save()
                emitter.onSuccess(true)
                //TODO Duplicated code
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                    view.refreshComponentState(componentName)
                }, { error ->
                    error?.apply {
                        ifwController.reload()
                        Log.e(TAG, message)
                        printStackTrace()
                        view.showAlertDialog()
                    }
                })
    }

    override fun launchActivity(component: ComponentInfo) {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            val command = "am start -n ${component.packageName}/${component.name}"
            try {
                RootCommand.runBlockingCommand(command)
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->

                }, { error ->
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view.showAlertDialog()
                    }
                })

    }

    override fun checkComponentEnableState(component: ComponentInfo): Boolean {
        return ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(component.packageName, component.name)) and
                ifwController.getComponentEnableState(component)
    }

    override fun start(context: Context) {

    }

    override fun destroy() {
        try {
            ifwController.save()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot save rules, message is ${e.message}")
        }
    }

    override var currentComparator: EComponentComparatorType = EComponentComparatorType.NAME_ASCENDING

    companion object {
        const val TAG = "ComponentPresenter"
    }
}