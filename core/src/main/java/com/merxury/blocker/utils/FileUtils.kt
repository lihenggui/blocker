package com.merxury.blocker.utils

import com.merxury.blocker.core.root.RootCommand
import com.stericson.RootTools.RootTools
import java.io.File

object FileUtils {
    fun copy(source: String, dest: String): Boolean{
        // TODO use Android default implementation in future version
        if(!RootTools.exists(dest, true)) {
            RootCommand.runBlockingCommand("mkdir -m 777 $dest")
        }
        RootCommand.runBlockingCommand("dd if=$source of=$dest")
        return RootTools.copyFile(source, dest, false, true)
    }
}