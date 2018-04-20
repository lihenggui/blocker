package com.merxury.blocker.ui.component

import android.content.Context
import android.util.Log
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo
import com.merxury.blocker.ui.strategy.service.ApiClient
import com.merxury.blocker.ui.strategy.service.IClientServer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Mercury on 2018/4/14.
 */

class ComponentDataPresenter(val view: ComponentContract.ComponentMainView) : ComponentContract.ComponentDataPresenter {

    private lateinit var componentData: AppComponentInfo
    private lateinit var client: IClientServer
    override fun start(context: Context) {
    }

    override fun destroy() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getComponentData(packageName: String): AppComponentInfo {
        if (!::componentData.isInitialized) {
            loadComponentData(packageName)
        }
        return componentData
    }

    override fun loadComponentData(packageName: String) {
        if (!::componentData.isInitialized) {
            refreshComponentData(packageName)
        }
    }

    override fun refreshComponentData(packageName: String) {
        if (!::client.isInitialized) {
            client = ApiClient.createClient()
        }
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


    companion object {
        const val TAG = "ComponentDataPresenter"
    }
}