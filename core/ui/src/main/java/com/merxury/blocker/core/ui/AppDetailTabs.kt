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

import com.merxury.blocker.core.ui.R.plurals

sealed class AppDetailTabs(val name: String, val title: Int = 0, open val count: Int = -1) {
    object Info : AppDetailTabs(INFO, title = plurals.app_info)
    data class Receiver(override val count: Int = 0) :
        AppDetailTabs(RECEIVER, title = plurals.receiver_with_count)

    data class Service(override val count: Int = 0) :
        AppDetailTabs(SERVICE, title = plurals.service_with_count)

    data class Activity(override val count: Int = 0) :
        AppDetailTabs(ACTIVITY, title = plurals.activity_with_count)

    data class Provider(override val count: Int = 0) :
        AppDetailTabs(PROVIDER, title = plurals.provider_with_count)

    override fun toString(): String {
        return "Screen name = $name, count = $count"
    }

    companion object {
        private const val INFO = "info"
        private const val RECEIVER = "receiver"
        private const val SERVICE = "service"
        private const val ACTIVITY = "activity"
        private const val PROVIDER = "provider"

        fun fromName(name: String?, count: Int = 0): AppDetailTabs = when (name) {
            INFO -> Info
            RECEIVER -> Receiver(count)
            SERVICE -> Service(count)
            ACTIVITY -> Activity(count)
            PROVIDER -> Provider(count)
            else -> throw IllegalArgumentException("Invalid screen name in detail page")
        }
    }
}
