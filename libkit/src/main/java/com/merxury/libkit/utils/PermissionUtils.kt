package com.merxury.libkit.utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PermissionUtils {
    suspend fun isRootAvailable(dispatcher: CoroutineDispatcher= Dispatchers.IO): Boolean {
        return when (Shell.isAppGrantedRoot()) {
            true -> true
            false -> false
            else -> requestRootPermission(dispatcher
            )
        }
    }

    private suspend fun requestRootPermission(dispatcher: CoroutineDispatcher): Boolean {
        return withContext(dispatcher) {
            Shell.cmd("su").exec().isSuccess
        }
    }
}
