package com.merxury.blocker.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import com.google.gson.Gson
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.ifw.IfwController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.data.app.AppComponentRepository
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.utils.ApplicationUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

class ComponentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppComponentRepositoryEntryPoint {
        fun appComponent(): AppComponentRepository
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            "getComponents" -> getBlockedComponents(arg)
            "blocks" -> controlComponent(arg, extras)
            else -> null
        }
    }

    private fun getBlockedComponents(packageName: String?): Bundle? = runBlocking {
        if (packageName == null) return@runBlocking null
        val context = context ?: return@runBlocking null
        val packageManager = context.packageManager ?: return@runBlocking null
        try {
            val ifwController = IfwController(context)
            val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
            val blockedComponents = mutableListOf<ShareCmpInfo.Component>()
            ApplicationUtil.getActivityList(packageManager, packageName).filter {
                !pmController.checkComponentEnableState(
                    it.packageName,
                    it.name
                ) || !ifwController.checkComponentEnableState(
                    it.packageName,
                    it.name
                )
            }
                .forEach {
                    blockedComponents.add(
                        ShareCmpInfo.Component(
                            it.packageName,
                            it.name,
                            block = true
                        )
                    )
                }
            ApplicationUtil.getServiceList(packageManager, packageName).filter {
                !pmController.checkComponentEnableState(
                    it.packageName,
                    it.name
                ) || !ifwController.checkComponentEnableState(
                    it.packageName,
                    it.name
                )
            }
                .forEach {
                    blockedComponents.add(
                        ShareCmpInfo.Component(
                            it.packageName,
                            it.name,
                            block = true
                        )
                    )
                }
            ApplicationUtil.getProviderList(packageManager, packageName).filter {
                !pmController.checkComponentEnableState(
                    it.packageName,
                    it.name
                ) || !ifwController.checkComponentEnableState(
                    it.packageName,
                    it.name
                )
            }
                .forEach {
                    blockedComponents.add(
                        ShareCmpInfo.Component(
                            it.packageName,
                            it.name,
                            block = true
                        )
                    )
                }
            ApplicationUtil.getReceiverList(packageManager, packageName).filter {
                !pmController.checkComponentEnableState(
                    it.packageName,
                    it.name
                ) || !ifwController.checkComponentEnableState(
                    it.packageName,
                    it.name
                )
            }
                .forEach {
                    blockedComponents.add(
                        ShareCmpInfo.Component(
                            it.packageName,
                            it.name,
                            block = true
                        )
                    )
                }
            val returnJson = Gson().toJson(ShareCmpInfo(packageName, blockedComponents))
            return@runBlocking bundleOf("cmp_list" to returnJson)
        } catch (e: Exception) {
            Log.e("ComponentProvider", "getBlockedComponents error:", e)
            return@runBlocking null
        }
    }

    private fun controlComponent(packageName: String?, data: Bundle?): Bundle? = runBlocking {
        if (packageName == null || data == null) return@runBlocking null
        val rawString = data.getString("cmp_list")?: return@runBlocking null
        val context = context ?: return@runBlocking null
        val appContext = context.applicationContext ?: return@runBlocking null
        val hintEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            AppComponentRepositoryEntryPoint::class.java
        )
        val appComponentRepository = hintEntryPoint.appComponent()
        try {
            val shareCmpInfo =
                Gson().fromJson(rawString, ShareCmpInfo::class.java)
            val controllerType = PreferenceUtil.getControllerType(context)
            val controller = ComponentControllerProxy.getInstance(controllerType, context)
            shareCmpInfo.components.forEach { component ->
                if (component.block) {
                    controller.disable(packageName, component.name)
                } else {
                    controller.enable(packageName, component.name)
                }
                appComponentRepository.getAppComponent(packageName, component.name)?.let {
                    if (controllerType == EControllerMethod.IFW && component.type != "provider") {
                        it.ifwBlocked = component.block
                    } else {
                        it.pmBlocked = component.block
                    }
                    Log.d("ComponentProvider", "update component: $it")
                    appComponentRepository.addAppComponents(it)
                }
            }
            // Returned, but seems that it's not used.
            return@runBlocking data
        } catch (e: Exception) {
            Log.e("ComponentProvider", "controlComponent error:", e)
            return@runBlocking null
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        // Not implemented
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Not implemented
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // Not implemented
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not implemented
        return 0
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.item/vnd.com.merxury.blocker.component"
    }
}