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

package com.merxury.blocker.core.ui

import com.merxury.blocker.core.ui.R.string

sealed class AppDetailTabs(val name: String, val title: Int = 0) {
    object Info : AppDetailTabs(INFO, title = string.app_info)
    object Receiver : AppDetailTabs(RECEIVER, title = string.receiver_with_count)

    object Service : AppDetailTabs(SERVICE, title = string.service_with_count)

    object Activity : AppDetailTabs(ACTIVITY, title = string.activity_with_count)

    object Provider : AppDetailTabs(PROVIDER, title = string.provider_with_count)

    override fun toString(): String {
        return "Screen name = $name"
    }

    companion object {
        private const val INFO = "info"
        private const val RECEIVER = "receiver"
        private const val SERVICE = "service"
        private const val ACTIVITY = "activity"
        private const val PROVIDER = "provider"

        fun fromName(name: String?): AppDetailTabs = when (name) {
            INFO -> Info
            RECEIVER -> Receiver
            SERVICE -> Service
            ACTIVITY -> Activity
            PROVIDER -> Provider
            else -> throw IllegalArgumentException("Invalid screen name in detail page")
        }
    }
}
