package com.merxury.blocker.ui.component

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import com.merxury.blocker.R
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.exception.RootUnavailableException
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.StringUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ManagerUtils
import com.merxury.libkit.utils.PermissionUtils
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

class ComponentPresenter(val context: Context, var view: ComponentContract.View?, val packageName: String) : ComponentContract.Presenter, IController {
    override var currentComparator: EComponentComparatorType = EComponentComparatorType.SIMPLE_NAME_ASCENDING
    private val pm: PackageManager
    private val ifwController by lazy { ComponentControllerProxy.getInstance(EControllerMethod.IFW, context) }
    private val controller: IController by lazy {
        val controllerType = PreferenceUtil.getControllerType(context)
        ComponentControllerProxy.getInstance(controllerType, context)
    }

    init {
        view?.presenter = this
        pm = context.packageManager
    }

    override fun start(context: Context) {

    }

    override fun destroy() {
        view = null
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
                .subscribe { components ->
                    view?.setLoadingIndicator(false)
                    if (components.isEmpty()) {
                        view?.showNoComponent()
                    } else {
                        view?.showComponentList(components.toMutableList())
                    }
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
                .subscribe { _, error ->
                    view?.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, "Error while switching component", error)
                        view?.showAlertDialog(StringUtil.getStackTrace(error))
                    }
                }
        return true
    }

    @SuppressLint("CheckResult")
    override fun enable(packageName: String, componentName: String): Boolean {
        Log.i(TAG, "Enable component: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                var result = true
                val controllerType = PreferenceUtil.getControllerType(context)
                if (controllerType == EControllerMethod.PM) {
                    if (!checkIFWState(packageName, componentName)) {
                        result = result && ComponentControllerProxy.getInstance(EControllerMethod.IFW, context).enable(packageName, componentName)
                    }
                } else if (controllerType == EControllerMethod.IFW) {
                    if (!ApplicationUtil.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))) {
                        result = result && ComponentControllerProxy.getInstance(EControllerMethod.PM, context).enable(packageName, componentName)
                    }
                }
                result = result && controller.enable(packageName, componentName)
                emitter.onSuccess(result)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { _, error ->
                    view?.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, "Error while enabling component:", error)
                        view?.showAlertDialog(StringUtil.getStackTrace(this))
                    }
                }
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
                .subscribe { _, error ->
                    view?.refreshComponentState(componentName)
                    error?.apply {
                        Log.e(TAG, "Error while disabling component:", error)
                        view?.showAlertDialog(StringUtil.getStackTrace(this))
                    }
                }
        return true
    }

    override fun sortComponentList(components: List<ComponentItemViewModel>, type: EComponentComparatorType): List<ComponentItemViewModel> {
        val sortedComponents = when (type) {
            EComponentComparatorType.SIMPLE_NAME_ASCENDING -> components.sortedBy { it.simpleName }
            EComponentComparatorType.SIMPLE_NAME_DESCENDING -> components.sortedByDescending { it.simpleName }
            EComponentComparatorType.NAME_ASCENDING -> components.sortedBy { it.name }
            EComponentComparatorType.NAME_DESCENDING -> components.sortedByDescending { it.name }
        }
        return sortedComponents.sortedWith(compareBy({ !it.state }, { !it.ifwState }))
    }

    @SuppressLint("CheckResult")
    override fun addToIFW(packageName: String, componentName: String, type: EComponentType) {
        Log.i(TAG, "Disable component via IFW: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                ifwController.disable(packageName, componentName)
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                    view?.refreshComponentState(componentName)
                }, { error ->
                    Log.e(TAG, "Error while disabling component:\n", error)
                    view?.showAlertDialog(StringUtil.getStackTrace(error))
                })
    }

    @SuppressLint("CheckResult")
    override fun removeFromIFW(packageName: String, componentName: String, type: EComponentType) {
        Log.i(TAG, "Disable component via IFW: $componentName")
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                ifwController.enable(packageName, componentName)
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                    view?.refreshComponentState(componentName)
                }, { error ->
                    Log.e(TAG, "Error while enable component:\n", error)
                    view?.showAlertDialog(StringUtil.getStackTrace(error))
                })
    }

    @SuppressLint("CheckResult")
    override fun launchActivity(packageName: String, componentName: String) {
        Single.create((SingleOnSubscribe<Boolean> { emitter ->
            try {
                ManagerUtils.launchActivity(packageName, componentName)
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _ ->
                }, { error ->
                    Log.e(TAG, "Error in launching activity($packageName/$componentName): ", error)
                    view?.showAlertDialog(StringUtil.getStackTrace(error))
                })
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(packageName, componentName))
    }

    override fun batchEnable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun batchDisable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkIFWState(packageName: String, componentName: String): Boolean {
        return ifwController.checkComponentEnableState(packageName, componentName)
    }

    override fun getComponentViewModel(packageName: String, componentName: String): ComponentItemViewModel {
        val viewModel = ComponentItemViewModel(packageName = packageName, name = componentName)
        viewModel.simpleName = componentName.split(".").last()
        updateComponentViewModel(viewModel)
        return viewModel
    }

    override fun updateComponentViewModel(viewModel: ComponentItemViewModel) {
        viewModel.state = ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(viewModel.packageName, viewModel.name))
        viewModel.ifwState = ifwController.checkComponentEnableState(viewModel.packageName, viewModel.name)
    }

    @SuppressLint("CheckResult")
    override fun disableAllComponents(packageName: String, type: EComponentType) {
        Observable.create((ObservableOnSubscribe<ComponentInfo> { emitter ->
            if (!PermissionUtils.isRootAvailable) {
                emitter.onError(RootUnavailableException())
                return@ObservableOnSubscribe
            }
            val components = getComponents(packageName, type)
            try {
                controller.batchDisable(components) {
                    emitter.onNext(it)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    view?.refreshComponentState(it.name)
                }, { error ->
                    Log.e(TAG, "Error in batch disable:", error)
                    view?.showAlertDialog(StringUtil.getStackTrace(error))
                    view?.showActionFail()
                }, {
                    view?.showActionDone()
                    loadComponents(packageName, type)
                })
    }

    @SuppressLint("CheckResult")
    override fun enableAllComponents(packageName: String, type: EComponentType) {
        Observable.create((ObservableOnSubscribe<ComponentInfo> { emitter ->
            if (!PermissionUtils.isRootAvailable) {
                emitter.onError(RootUnavailableException())
                return@ObservableOnSubscribe
            }
            val components = getComponents(packageName, type)
            try {
                ifwController.batchEnable(components) {
                    if (!ApplicationUtil.checkComponentIsEnabled(context.packageManager, ComponentName(it.packageName, it.name))) {
                        if (PreferenceUtil.getControllerType(context) == EControllerMethod.SHIZUKU) {
                            ComponentControllerProxy.getInstance(EControllerMethod.SHIZUKU, context).enable(it.packageName, it.name)
                        } else {
                            ComponentControllerProxy.getInstance(EControllerMethod.PM, context).enable(it.packageName, it.name)
                        }
                    }
                    emitter.onNext(it)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    view?.refreshComponentState(it.name)
                }, { error ->
                    Log.e(TAG, "Error in batch enable:", error)
                    view?.showAlertDialog(StringUtil.getStackTrace(error))
                    view?.showActionFail()
                }, {
                    view?.showActionDone()
                    loadComponents(packageName, type)
                })
    }

    @SuppressLint("CheckResult")
    override fun exportRule(packageName: String) {
        RxPermissions(context as Activity)
                .request(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        exportBlockerRule(packageName)
                    } else {
                        view?.showActionFail()
                    }
                }
    }

    @SuppressLint("CheckResult")
    private fun exportBlockerRule(packageName: String) {
        Single.create(SingleOnSubscribe<RulesResult> { emitter ->
            try {
                val result = Rule.export(context, packageName)
                emitter.onSuccess(result)
            } catch (e: Exception) {
                emitter.onError(e)
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.isSucceed) {
                        view?.showActionDone()
                    } else {
                        view?.showActionFail()
                    }
                }, {
                    Log.e(TAG, "Error in exporting blocker rules:", it)
                    view?.showAlertDialog(StringUtil.getStackTrace(it))
                })
    }

    @SuppressLint("CheckResult")
    override fun importRule(packageName: String) {
        RxPermissions(context as Activity)
                .request(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        importBlockerRule(packageName)
                    } else {
                        view?.showActionFail()
                    }
                }
    }

    @SuppressLint("CheckResult")
    private fun importBlockerRule(packageName: String) {
        view?.showToastMessage(context.getString(R.string.processing), Toast.LENGTH_SHORT)
        Single.create(SingleOnSubscribe<RulesResult> { emitter ->
            val blockerFolder = Rule.getBlockerRuleFolder(context)
            val destFile = File(blockerFolder, packageName + Rule.EXTENSION)
            if (!destFile.exists()) {
                emitter.onSuccess(RulesResult(false, 0, 0))
                return@SingleOnSubscribe
            }
            val result = Rule.import(context, destFile)
            emitter.onSuccess(result)
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.isSucceed) {
                        ToastUtil.showToast(context.getString(R.string.done))
                    } else {
                        ToastUtil.showToast(context.getString(R.string.import_fail_message))
                    }
                }, {
                    Log.e(TAG, "Error in importing blocker rules:", it)
                    view?.showAlertDialog(StringUtil.getStackTrace(it))
                })
    }

    private fun initViewModel(componentList: List<ComponentInfo>): List<ComponentItemViewModel> {
        val viewModels = ArrayList<ComponentItemViewModel>()
        componentList.forEach {
            viewModels.add(getComponentViewModel(it.packageName, it.name))
        }
        return viewModels
    }

    private fun getComponents(packageName: String, type: EComponentType): MutableList<out ComponentInfo> {
        return when (type) {
            EComponentType.RECEIVER -> ApplicationUtil.getReceiverList(pm, packageName)
            EComponentType.ACTIVITY -> ApplicationUtil.getActivityList(pm, packageName)
            EComponentType.SERVICE -> ApplicationUtil.getServiceList(pm, packageName)
            EComponentType.PROVIDER -> ApplicationUtil.getProviderList(pm, packageName)
            else -> ArrayList()
        }
    }

    companion object {
        const val TAG = "ComponentPresenter"
    }
}