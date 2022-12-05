package com.merxury.blocker.core.entity

import android.content.pm.ComponentInfo

fun ComponentInfo.getSimpleName(): String {
    return name.split(".").last()
}
