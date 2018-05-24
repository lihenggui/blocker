package com.merxury.blocker.utils

import com.stericson.RootTools.RootTools

object FileUtils {
    fun copy(source: String, dest: String) {
        RootTools.copyFile(source, dest, false, true)
    }
}