package com.merxury.libkit.utils

import com.topjohnwu.superuser.Shell

object PermissionUtils {
    val isRootAvailable: Boolean
        get() = Shell.rootAccess()
}
