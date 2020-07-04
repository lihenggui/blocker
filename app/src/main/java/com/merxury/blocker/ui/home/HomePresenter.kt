package com.merxury.blocker.ui.home

import android.content.Context
import android.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.AppLauncher
import com.merxury.blocker.util.DialogUtil
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.ETrimMemoryLevel
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ManagerUtils
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class HomePresenter(private var homeView: HomeContract.View?) : HomeContract.Presenter, CoroutineScope {
    private lateinit var job: Job
    private var context: Context? = null
    private val logger = XLog.tag(this.javaClass.simpleName).build()
    private val exceptionHandler = { e: Throwable ->
        GlobalScope.launch(Dispatchers.Main) {
            DialogUtil().showWarningDialogWithMessage(context, e)
        }
        logger.e(e)
    }
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun start(context: Context) {
        job = Job()
        this.context = context
        homeView?.presenter = this
    }

    override fun destroy() {
        context = null
        homeView = null
        job.cancel()
    }

    override fun loadApplicationList(context: Context, isSystemApplication: Boolean) {
        homeView?.setLoadingIndicator(true)
        launch {
            val sortedList = withContext(Dispatchers.IO) {
                val applications: MutableList<Application> = when (isSystemApplication) {
                    false -> ApplicationUtil.getThirdPartyApplicationList(context)
                    true -> ApplicationUtil.getSystemApplicationList(context)
                }
                sortApplicationList(applications)
            }
            if (sortedList.isEmpty()) {
                homeView?.showNoApplication()
            } else {
                homeView?.showApplicationList(sortedList)
            }
            homeView?.setLoadingIndicator(false)
        }
    }

    override fun openApplicationDetails(application: Application) {
        homeView?.showApplicationDetailsUi(application)
    }

    override fun sortApplicationList(applications: List<Application>): MutableList<Application> {
        val sortedList = when (currentComparator) {
            ApplicationComparatorType.ASCENDING_BY_LABEL -> applications.asSequence().sortedBy { it.label }
            ApplicationComparatorType.DESCENDING_BY_LABEL -> applications.asSequence().sortedByDescending { it.label }
            ApplicationComparatorType.INSTALLATION_TIME -> applications.asSequence().sortedByDescending { it.firstInstallTime }
            ApplicationComparatorType.LAST_UPDATE_TIME -> applications.asSequence().sortedByDescending { it.lastUpdateTime }
        }
        return sortedList.asSequence().sortedWith(compareBy({ !it.isBlocked }, { !it.isEnabled })).toMutableList()
    }

    override fun launchApplication(packageName: String) {
        context?.let {
            AppLauncher.startApplication(it, packageName)
        }
    }

    override fun forceStop(packageName: String) {
        launch (Dispatchers.IO) {
            ManagerUtils.forceStop(packageName)
        }
    }

    override fun enableApplication(packageName: String) {
        launch {
            withContext(Dispatchers.IO) {ManagerUtils.enableApplication(packageName)}
            homeView?.updateState(packageName)
        }
    }

    override fun disableApplication(packageName: String) {
        launch {
            withContext(Dispatchers.IO) {ManagerUtils.disableApplication(packageName)}
            homeView?.updateState(packageName)
        }
    }

    override fun clearData(packageName: String) {
       launch {
           withContext(Dispatchers.IO) { ManagerUtils.clearData(packageName) }
           homeView?.showDataCleared()
       }
    }

    override fun trimMemory(packageName: String, level: ETrimMemoryLevel) {
        launch(Dispatchers.IO) {
            ManagerUtils.trimMemory(packageName, level)
        }
    }

    override fun showDetails(packageName: String) {
        context?.let {
            AppLauncher.showApplicationDetails(it, packageName)
        }
    }

    override fun blockApplication(packageName: String) {
        launch {
            withContext(Dispatchers.IO) {ApplicationUtil.addBlockedApplication(context!!, packageName)}
            homeView?.updateState(packageName)
        }
    }

    override fun unblockApplication(packageName: String) {
        launch {
            withContext(Dispatchers.IO) {ApplicationUtil.removeBlockedApplication(context!!, packageName)}
            homeView?.updateState(packageName)
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