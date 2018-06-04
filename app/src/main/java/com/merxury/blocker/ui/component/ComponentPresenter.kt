package com.merxury.blocker.ui.component

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.util.Log
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.root.RootCommand
import com.merxury.blocker.core.shizuku.ShizukuClientWrapper
import com.merxury.blocker.exception.RootUnavailableException
import com.merxury.blocker.strategy.entity.view.ComponentBriefInfo
import com.merxury.blocker.strategy.service.ApiClient
import com.merxury.blocker.strategy.service.IClientServer
import com.merxury.blocker.ui.settings.general.GeneralPreferenceFragment.Companion.KEY_PREF_CONTROLLER_TYPE
import com.merxury.blocker.ui.settings.general.GeneralPreferenceFragment.Companion.KEY_PREF_CONTROLLER_TYPE_DEFAULT
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import com.merxury.ifw.util.PermissionUtils
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiConsumer
import io.reactivex.schedulers.Schedulers

class ComponentPresenter(val context: Context, var view: ComponentContract.View?, val packageName: String) : ComponentContract.Presenter, IController {

    private val pm: PackageManager

    private val controller: IController by lazy {
        val controllerType = getControllerType(context)
        ComponentControllerProxy.getInstance(controllerType, context)
    }


    private val componentClient: IClientServer by lazy {
        ApiClient.createClient()
    }

    private val ifwController: IntentFirewall by lazy {
        IntentFirewallImpl.getInstance(context, packageName)
    }

    init {
        view?.presenter = this
        pm = context.packageManager
    }

    @SuppressLint("CheckResult")
    override fun loadComponents(packageName: String, type: EComponentType) {
        Log.i(TAG, "Load components for $packageName, type: $type")
        view?.setLoadingIndicator(true)
        Single.create((SingleOnSubscribe<List<ComponentItemViewModel>> { emitter ->
            val componentList = getComponents(packageName, type)
            var viewModels = initViewModel(componentList)
            viewModels = sortComponentList(viewModels, currentComparator)
            emitter.onSuccess(viewModels)
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ components ->
                    view?.setLoadingIndicator(false)
                    if (components.isEmpty()) {
                        view?.showNoComponent()
                    } else {
                        view?.showComponentList(components.toMutableList())
                    }
                })
    }

    private fun getComponents(packageName: String, type: EComponentType): MutableList<ComponentInfo> {
        return when (type) {
            EComponentType.RECEIVER -> ApplicationComponents.getReceiverList(pm, packageName)
            EComponentType.ACTIVITY -> ApplicationComponents.getActivityList(pm, packageName)
            EComponentType.SERVICE -> ApplicationComponents.getServiceList(pm, packageName)
            EComponentType.PROVIDER -> ApplicationComponents.getProviderList(pm, packageName)
            else -> ArrayList()
        }
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
                    view?.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
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
                    view?.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
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
                    view?.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
                    }
                })
        return true
    }

    override fun sortComponentList(components: List<ComponentItemViewModel>, type: EComponentComparatorType): List<ComponentItemViewModel> {
        return when (type) {
            EComponentComparatorType.SIMPLE_NAME_ASCENDING -> components.sortedBy { it.simpleName }
            EComponentComparatorType.SIMPLE_NAME_DESCENDING -> components.sortedByDescending { it.simpleName }
            EComponentComparatorType.NAME_ASCENDING -> components.sortedBy { it.name }
            EComponentComparatorType.NAME_DESCENDING -> components.sortedByDescending { it.name }
        }
    }

    override fun checkComponentIsUpVoted(packageName: String, componentName: String): Boolean {
        val sharedPreferences = context.getSharedPreferences(packageName, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(componentName + UPVOTED, false)
    }

    override fun checkComponentIsDownVoted(packageName: String, componentName: String): Boolean {
        val sharedPreferences = context.getSharedPreferences(packageName, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(componentName + DOWNVOTED, false)
    }


    override fun writeComponentVoteState(packageName: String, componentName: String, like: Boolean) {
        val sharedPreferences = context.getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        if (like) {
            editor.putBoolean(componentName + UPVOTED, like)
        } else {
            editor.putBoolean(componentName + DOWNVOTED, like)
        }
        editor.apply()
    }

    @SuppressLint("CheckResult")
    override fun voteForComponent(packageName: String, componentName: String, type: EComponentType) {
        componentClient.upVoteForComponent(ComponentBriefInfo(packageName, componentName, type))
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    writeComponentVoteState(packageName, componentName, true)
                    view?.refreshComponentState(componentName)
                }, { error ->

                })
    }

    @SuppressLint("CheckResult")
    override fun downVoteForComponent(packageName: String, componentName: String, type: EComponentType) {
        componentClient.downVoteForComponent(ComponentBriefInfo(packageName, componentName, type))
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    writeComponentVoteState(packageName, componentName, false)
                    view?.refreshComponentState(componentName)
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
                    view?.refreshComponentState(componentName)
                }, { error ->
                    error?.apply {
                        ifwController.reload()
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
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
                    view?.refreshComponentState(componentName)
                }, { error ->
                    error?.apply {
                        ifwController.reload()
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
                    }
                })
    }

    override fun launchActivity(packageName: String, componentName: String) {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            val command = "am start -n $packageName/$componentName"
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
                        view?.showAlertDialog()
                    }
                })

    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(packageName, componentName))

    }

    override fun checkIFWState(packageName: String, componentName: String): Boolean {
        return ifwController.getComponentEnableState(packageName, componentName)
    }

    override fun getComponentViewModel(packageName: String, componentName: String): ComponentItemViewModel {
        val viewModel = ComponentItemViewModel(packageName = packageName, name = componentName)
        viewModel.simpleName = componentName.split(".").last()
        updateComponentViewModel(viewModel)
        return viewModel
    }

    override fun updateComponentViewModel(viewModel: ComponentItemViewModel) {
        viewModel.state = ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(viewModel.packageName, viewModel.name))
        viewModel.ifwState = ifwController.getComponentEnableState(viewModel.packageName, viewModel.name)
        viewModel.upVoted = checkComponentIsUpVoted(viewModel.packageName, viewModel.name)
        viewModel.downVoted = checkComponentIsDownVoted(viewModel.packageName, viewModel.name)
    }

    override fun disableAllComponents(packageName: String, type: EComponentType) {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            if (!PermissionUtils.isRootAvailable()) {
                emitter.onError(RootUnavailableException())
                return@SingleOnSubscribe
            }
            val components = getComponents(packageName, type)
            try {
                components.forEach {
                    when (type) {
                        EComponentType.ACTIVITY -> ifwController.add(it.packageName, it.name, ComponentType.ACTIVITY)
                        EComponentType.SERVICE -> ifwController.add(it.packageName, it.name, ComponentType.SERVICE)
                        EComponentType.RECEIVER -> ifwController.add(it.packageName, it.name, ComponentType.BROADCAST)
                        else -> {
                        }
                    }
                }
                ifwController.save()
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorReturn { false }
                .subscribe({ result, error ->
                    loadComponents(packageName, type)
                    if (result) {
                        view?.showActionDone()
                    } else {
                        view?.showActionFail()
                    }
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
                    }
                })

    }

    override fun enableAllComponents(packageName: String, type: EComponentType) {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            if (!PermissionUtils.isRootAvailable()) {
                emitter.onError(RootUnavailableException())
                return@SingleOnSubscribe
            }
            val components = getComponents(packageName, type)
            try {
                components.forEach {
                    when (type) {
                        EComponentType.ACTIVITY -> ifwController.remove(it.packageName, it.name, ComponentType.ACTIVITY)
                        EComponentType.SERVICE -> ifwController.remove(it.packageName, it.name, ComponentType.SERVICE)
                        EComponentType.RECEIVER -> ifwController.remove(it.packageName, it.name, ComponentType.BROADCAST)
                        else -> emitter.onSuccess(true)
                    }
                }
                ifwController.save()
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorReturn { false }
                .subscribe({ result, error ->
                    if (result) {
                        view?.showActionDone()
                    } else {
                        view?.showActionFail()
                    }
                    loadComponents(packageName, type)
                    error?.apply {
                        Log.e(TAG, message)
                        printStackTrace()
                        view?.showAlertDialog()
                    }
                })

    }


    private fun initViewModel(componentList: List<ComponentInfo>): List<ComponentItemViewModel> {
        val viewModels = ArrayList<ComponentItemViewModel>()
        componentList.forEach {
            viewModels.add(getComponentViewModel(it.packageName, it.name))
        }
        return viewModels
    }

    private fun requestShizukuPermission() {
        RxPermissions(context as Activity)
                .request(ShizukuClientWrapper.PERMISSION_V23)
                .subscribe { granted ->

                }
    }

    private fun getControllerType(context: Context): EControllerMethod {
        // Magic value, but still use it.
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return when (pref.getString(KEY_PREF_CONTROLLER_TYPE, KEY_PREF_CONTROLLER_TYPE_DEFAULT)) {
            "shizuku" -> EControllerMethod.SHIZUKU
            else -> EControllerMethod.PM
        }
    }


    override fun start(context: Context) {
        if (getControllerType(context) == EControllerMethod.SHIZUKU) {
            requestShizukuPermission()
        }
    }

    override fun destroy() {
        view = null;
    }

    override var currentComparator: EComponentComparatorType = EComponentComparatorType.SIMPLE_NAME_ASCENDING

    companion object {
        const val TAG = "ComponentPresenter"
        const val UPVOTED = "_voted"
        const val DOWNVOTED = "_downvoted"
    }
}