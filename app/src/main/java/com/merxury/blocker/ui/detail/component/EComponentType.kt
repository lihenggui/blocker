package com.merxury.blocker.ui.detail.component

import com.merxury.ifw.entity.ComponentType

enum class EComponentType {
    RECEIVER,
    ACTIVITY,
    SERVICE,
    PROVIDER
}

fun EComponentType.toComponentType(): ComponentType {
    return when (this) {
        EComponentType.RECEIVER -> ComponentType.BROADCAST
        EComponentType.ACTIVITY -> ComponentType.ACTIVITY
        EComponentType.SERVICE -> ComponentType.SERVICE
        EComponentType.PROVIDER -> ComponentType.PROVIDER
    }
}