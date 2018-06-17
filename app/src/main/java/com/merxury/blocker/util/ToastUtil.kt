package com.merxury.blocker.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.widget.Toast

import com.merxury.blocker.BlockerApplication

object ToastUtil {
    private var toast: Toast? = null
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            toast?.cancel()
            val message = msg.obj as String
            toast = Toast.makeText(BlockerApplication.context, message, msg.arg2)
            toast?.show()
        }
    }

    fun showToast(message: String, duration: Int) {
        handler.sendMessage(handler.obtainMessage(0, 0, duration, message))
    }

    fun showToast(message: String) {
        if (!TextUtils.isEmpty(message)) {
            showToast(message, Toast.LENGTH_SHORT)
        }
    }
}