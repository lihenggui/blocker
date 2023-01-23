/*
 * Copyright 2023 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.StringRes
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

    fun showToast(@StringRes msgId: Int, duration: Int) {
        val message = BlockerApplication.context.getString(msgId)
        handler.sendMessage(handler.obtainMessage(0, 0, duration, message))
    }

    fun showToast(message: String) {
        if (!TextUtils.isEmpty(message)) {
            showToast(message, Toast.LENGTH_SHORT)
        }
    }
}
