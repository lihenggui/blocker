package com.merxury.blocker.ui.component

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.exception.RootUnavailableException
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.RulesResult
import com.merxury.blocker.util.DialogUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.entity.getSimpleName
import com.merxury.libkit.utils.*
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File

class ComponentPresenter(val context: Context, var view: ComponentContract.View?, val packageName: String) : ComponentContract.Presenter, IController {
    override var currentComparator: EComponentComparatorType = EComponentComparatorType.SIMPLE_NAME_ASCENDING
    private val pm: PackageManager
    private val logger = XLog.tag(this.javaClass.simpleName).build()
    private val serviceHelper by lazy { ServiceHelper(packageName) }
    private val ifwController by lazy { ComponentControllerProxy.getInstance(EControllerMethod.IFW, context) }
    private val controller: IController by lazy {
        val controllerType = PreferenceUtil.getControllerType(context)
        ComponentControllerProxy.getInstance(controllerType, context)
    }
    private val exceptionHandler = { e: Throwable ->
        GlobalScope.launch(Dispatchers.Main) {
            DialogUtil().showWarningDialogWithMessage(context, e)
        }
        logger.e(e)
    }
    private lateinit var type: EComponentType

    init {
        view?.presenter = this
        pm = context.packageManager
    }

    override fun start(context: Context) {

    }

    override fun destroy() {
        view = null
    }

    override fun loadComponents(packageName: String, type: EComponentType) {
        logger.i("Load components for $packageName, type: $type")
        view?.setLoadingIndicator(true)
        doAsync(exceptionHandler) {
            if (type == EComponentType.SERVICE) {
                serviceHelper.refresh()
            }
            val componentList = getComponents(packageName, type)
            val viewModels = initViewModel(componentList)
            val sortedViewModels = sortComponentList(viewModels, currentComparator)
            uiThread {
                view?.setLoadingIndicator(false)
                if (sortedViewModels.isEmpty()) {
                    view?.showNoComponent()
                } else {
                    view?.showComponentList(sortedViewModels.toMutableList())
                }
            }
        }
    }

    override fun switchComponent(packageName: String, componentName: String, state: Int): Boolean {
        return controller.switchComponent(packageName, componentName, state)
    }

    override fun enable(packageName: String, componentName: String): Boolean {
        logger.i("Enable component: $componentName")
        val handler = { e: Throwable ->
            GlobalScope.launch(Dispatchers.Main) {
                DialogUtil().showWarningDialogWithMessage(context, e)
                view?.refreshComponentState(componentName)
            }
            logger.e(e)
        }
        doAsync(handler) {
            val controllerType = PreferenceUtil.getControllerType(context)
            if (controllerType == EControllerMethod.PM) {
                if (!checkIFWState(packageName, componentName)) {
                    ComponentControllerProxy.getInstance(EControllerMethod.IFW, context).enable(packageName, componentName)
                }
            } else if (controllerType == EControllerMethod.IFW) {
                if (!ApplicationUtil.checkComponentIsEnabled(context.packageManager, ComponentName(packageName, componentName))) {
                    ComponentControllerProxy.getInstance(EControllerMethod.PM, context).enable(packageName, componentName)
                }
            }
            controller.enable(packageName, componentName)
            uiThread {
                view?.refreshComponentState(componentName)
            }
        }
        return true
    }

    override fun disable(packageName: String, componentName: String): Boolean {
        logger.i("Disable component: $componentName")
        val handler = { e: Throwable ->
            GlobalScope.launch(Dispatchers.Main) {
                DialogUtil().showWarningDialogWithMessage(context, e)
                view?.refreshComponentState(componentName)
            }
            logger.e(e)
        }
        doAsync(handler) {
            controller.disable(packageName, componentName)
            uiThread {
                view?.refreshComponentState(componentName)
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
        return sortedComponents.sortedWith(compareBy({ !it.isRunning }, { !it.state }, { !it.ifwState }))
    }

    override fun addToIFW(packageName: String, componentName: String, type: EComponentType) {
        logger.i("Disable component via IFW: $componentName")
        doAsync(exceptionHandler) {
            ifwController.disable(packageName, componentName)
            uiThread {
                view?.refreshComponentState(componentName)
            }
        }
    }

    override fun removeFromIFW(packageName: String, componentName: String, type: EComponentType) {
        logger.i("Disable component via IFW: $componentName")
        doAsync(exceptionHandler) {
            ifwController.enable(packageName, componentName)
            uiThread {
                view?.refreshComponentState(componentName)
            }
        }
    }

    override fun launchActivity(packageName: String, componentName: String) {
        doAsync(exceptionHandler) {
            ManagerUtils.launchActivity(packageName, componentName)
        }
    }

    override fun checkComponentEnableState(packageName: String, componentName: String): Boolean {
        return ApplicationUtil.checkComponentIsEnabled(pm, ComponentName(packageName, componentName))
    }

    override fun batchEnable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        TODO("Won't implemented")
    }

    override fun batchDisable(componentList: List<ComponentInfo>, action: (info: ComponentInfo) -> Unit): Int {
        TODO("Won't  implemented")
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
        if (type == EComponentType.SERVICE) {
            viewModel.isRunning = isServiceRunning(viewModel.name)
        }
    }

    override fun disableAllComponents(packageName: String, type: EComponentType) {
        doAsync(exceptionHandler) {
            if (!PermissionUtils.isRootAvailable) {
                throw RootUnavailableException()
            }
            val components = getComponents(packageName, type)
            controller.batchDisable(components) { componentInfo ->
                uiThread {
                    view?.refreshComponentState(componentInfo.name)
                }
            }
            uiThread {
                view?.showActionDone()
                loadComponents(packageName, type)
            }
        }
    }

    override fun enableAllComponents(packageName: String, type: EComponentType) {
        doAsync(exceptionHandler) {
            if (!PermissionUtils.isRootAvailable) {
                throw RootUnavailableException()
            }
            val components = getComponents(packageName, type)
            ifwController.batchEnable(components) { componentInfo ->
                if (!ApplicationUtil.checkComponentIsEnabled(context.packageManager, ComponentName(componentInfo.packageName, componentInfo.name))) {
                    if (PreferenceUtil.getControllerType(context) == EControllerMethod.SHIZUKU) {
                        ComponentControllerProxy.getInstance(EControllerMethod.SHIZUKU, context).enable(componentInfo.packageName, componentInfo.name)
                    } else {
                        ComponentControllerProxy.getInstance(EControllerMethod.PM, context).enable(componentInfo.packageName, componentInfo.name)
                    }
                }
                uiThread {
                    view?.refreshComponentState(componentInfo.name)
                }
            }
            uiThread {
                view?.showActionDone()
                loadComponents(packageName, type)
            }
        }
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

    private fun exportBlockerRule(packageName: String) {
        doAsync(exceptionHandler) {
            val result = Rule.export(context, packageName)
            uiThread {
                if (result.isSucceed) {
                    view?.showActionDone()
                } else {
                    view?.showActionFail()
                }
            }
        }
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

    override fun isServiceRunning(componentName: String): Boolean {
        return serviceHelper.isServiceRunning(componentName)
    }

    @SuppressLint("CheckResult")
    private fun importBlockerRule(packageName: String) {
        view?.showToastMessage(context.getString(R.string.processing), Toast.LENGTH_SHORT)
        doAsync(exceptionHandler) {
            val blockerFolder = Rule.getBlockerRuleFolder(context)
            val destFile = File(blockerFolder, packageName + Rule.EXTENSION)
            val result =
                    if (!destFile.exists()) {
                        RulesResult(false, 0, 0)
                    } else {
                        Rule.import(context, destFile)
                    }
            uiThread {
                if (result.isSucceed) {
                    ToastUtil.showToast(context.getString(R.string.done))
                } else {
                    ToastUtil.showToast(context.getString(R.string.import_fail_message))
                }
            }
        }
    }

    private fun initViewModel(componentList: List<ComponentInfo>): List<ComponentItemViewModel> {
        val viewModels = ArrayList<ComponentItemViewModel>()
        componentList.forEach {
            viewModels.add(getComponentViewModel(it.packageName, it.name))
        }
        return viewModels
    }

    private fun getComponents(packageName: String, type: EComponentType): MutableList<out ComponentInfo> {
        this.type = type
        val components = when (type) {
            EComponentType.RECEIVER -> ApplicationUtil.getReceiverList(pm, packageName)
            EComponentType.ACTIVITY -> ApplicationUtil.getActivityList(pm, packageName)
            EComponentType.SERVICE -> ApplicationUtil.getServiceList(pm, packageName)
            EComponentType.PROVIDER -> ApplicationUtil.getProviderList(pm, packageName)
            else -> ArrayList<ComponentInfo>()
        }
        return components.asSequence().sortedBy { it.getSimpleName() }.toMutableList()
    }
}