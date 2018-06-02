package com.merxury.blocker.core.shizuku

import android.content.Context
import moe.shizuku.api.ShizukuClient
import java.util.*

class ShizukuClientWrapper {
    companion object {
        const val REQUEST_CODE_AUTHORIZATION = ShizukuClient.REQUEST_CODE_AUTHORIZATION
        const val REQUEST_CODE_PERMISSION = ShizukuClient.REQUEST_CODE_PERMISSION
        const val PERMISSION = ShizukuClient.PERMISSION
        const val PERMISSION_V23 = ShizukuClient.PERMISSION_V23
        const val AUTH_RESULT_OK = ShizukuClient.AUTH_RESULT_OK
        const val AUTH_RESULT_USER_DENIED = ShizukuClient.AUTH_RESULT_USER_DENIED

        fun initialize(context: Context) {
            ShizukuClient.initialize(context)
        }

        fun setToken(uuid: UUID) {
            ShizukuClient.setToken(uuid)
        }

        fun isManagerInstalled(context: Context): Boolean {
            return ShizukuClient.isManagerInstalled(context)
        }

    }
}
