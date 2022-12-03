package com.merxury.libkit.entity

import android.content.pm.ComponentInfo

fun ComponentInfo.getSimpleName(): String {
    return name.split(".").last()
}
