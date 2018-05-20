package com.merxury.blocker.ui.strategy.entity.view

import android.content.pm.ComponentInfo
import com.merxury.blocker.ui.component.EComponentType

data class ComponentBriefInfo(
        var packageName: String = "",
        var name: String = "",
        var type: EComponentType = EComponentType.UNKNOWN
) {

    constructor(componentInfo: ComponentInfo) :
            this(componentInfo.packageName, componentInfo.name)

    fun toQueryMap(): HashMap<String, String> {
        val result = HashMap<String, String>()
        result["packageName"] = packageName
        result["name"] = name
        result["type"] = type.toString()
        return result
    }
}