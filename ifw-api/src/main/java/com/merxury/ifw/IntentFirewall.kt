package com.merxury.ifw

import com.merxury.ifw.entity.ComponentType

interface IntentFirewall {
    @Throws(Exception::class)
    suspend fun save()

    @Throws(Exception::class)
    suspend fun load(): IntentFirewall
    fun add(packageName: String, componentName: String, type: ComponentType?): Boolean
    fun remove(packageName: String, componentName: String, type: ComponentType?): Boolean
    fun getComponentEnableState(packageName: String, componentName: String): Boolean

    @Throws(Exception::class)
    suspend fun clear()
    val packageName: String
}