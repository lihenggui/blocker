package com.merxury.blocker.core

import android.content.pm.ComponentInfo

/**
 * Created by Mercury on 2018/1/13.
 * An Interface that defines what controller should do
 */

interface IController {

    /**
     * a method to change a component's state
     *
     * @param packageName   package name
     * @param componentName component name
     * @param state         PackageManager.COMPONENT_ENABLED_STATE_ENABLED: enable component
     * COMPONENT_ENABLED_STATE_DISABLED: disable component
     * @return true : changed component state successfully
     * false: cannot disable component
     */
    suspend fun switchComponent(packageName: String, componentName: String, state: Int): Boolean

    suspend fun enable(packageName: String, componentName: String): Boolean

    suspend fun disable(packageName: String, componentName: String): Boolean

    suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: (info: ComponentInfo) -> Unit
    ): Int

    suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: (info: ComponentInfo) -> Unit
    ): Int

    suspend fun checkComponentEnableState(packageName: String, componentName: String): Boolean
}
