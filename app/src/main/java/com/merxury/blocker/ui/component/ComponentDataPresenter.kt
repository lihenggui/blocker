package com.merxury.blocker.ui.component

import android.content.Context
import android.content.pm.ComponentInfo
import android.util.Log
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo
import com.merxury.blocker.ui.strategy.service.ApiClient
import com.merxury.blocker.ui.strategy.service.IClientServer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Mercury on 2018/4/14.
 */

class ComponentDataPresenter(val view: ComponentContract.ComponentMainView, override val packageName: String) : ComponentContract.ComponentDataPresenter {

    private lateinit var componentData: AppComponentInfo
    private val client: IClientServer by lazy {
        ApiClient.createClient()
    }

    override fun start(context: Context) {
    }

    override fun destroy() {

    }

    override fun getComponentData(): AppComponentInfo {
        if (!::componentData.isInitialized) {
            loadComponentData()
        }
        return componentData
    }

    override fun loadComponentData() {
        if (!::componentData.isInitialized) {
            refreshComponentData()
        }
    }

    override fun refreshComponentData() {
        client.findAllComponentsByPackageName(packageName)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    componentData = result.data
                    view.onComponentLoaded(componentData)
                    Log.i(TAG, "Get components for $packageName from server successfully.")
                }, { error ->
                    error.printStackTrace()
                    Log.e(TAG, "Error occurs while getting component data from server. The message is : ${error.message}")
                })
    }

    override fun sendComment(component: ComponentInfo, comment: String) {
    }


    companion object {
        const val TAG = "ComponentDataPresenter"
    }
}