package com.merxury.blocker.ui.component

import android.content.Context
import android.util.Log
import com.merxury.blocker.ui.strategy.entity.ComponentDescription
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo
import com.merxury.blocker.ui.strategy.service.ApiClient
import com.merxury.blocker.ui.strategy.service.IClientServer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Mercury on 2018/4/14.
 */

class ComponentOnlineDataPresenter(val view: ComponentContract.ComponentMainView, override val packageName: String) : ComponentContract.ComponentOnlineDataPresenter {

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

    override fun sendDescription(packageName: String, componentName: String, type: EComponentType, description: String) {
        val componentDescription = ComponentDescription(name = componentName, packageName = packageName,
                type = type, description = description)
        client.addDescription(componentDescription)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result -> }, { error ->
                    error.printStackTrace()
                    Log.e(TAG, "Error occurs while sending description to server, Message is : ${error.message}")
                })
    }


    companion object {
        const val TAG = "ComponentOnlineDataPres"
    }
}