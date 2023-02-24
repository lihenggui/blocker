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

package com.merxury.blocker.feature.search

sealed class SearchScreenTabs(val name: String, val title: Int = 0, val itemCount: Int = 0) {
    data class App(val count: Int = 0) :
        SearchScreenTabs(APP, title = R.string.application_with_count, itemCount = count)

    data class Component(val count: Int = 0) :
        SearchScreenTabs(COMPONENT, title = R.string.component_with_count, itemCount = count)

    data class Rule(val count: Int = 0) :
        SearchScreenTabs(RULE, title = R.string.online_rule_with_count, itemCount = count)

    data class Receiver(val count: Int = 0) :
        SearchScreenTabs(RECEIVER, title = R.string.receiver_with_count, itemCount = count)

    data class Service(val count: Int = 0) :
        SearchScreenTabs(SERVICE, title = R.string.service_with_count, itemCount = count)

    data class Activity(val count: Int = 0) :
        SearchScreenTabs(ACTIVITY, title = R.string.activity_with_count, itemCount = count)

    data class Provider(val count: Int = 0) :
        SearchScreenTabs(PROVIDER, title = R.string.provider_with_count, itemCount = count)

    override fun toString(): String {
        return "Screen name = $name"
    }

    companion object {
        private const val APP = "app"
        private const val COMPONENT = "component"
        private const val RULE = "rule"
        private const val RECEIVER = "receiver"
        private const val SERVICE = "service"
        private const val ACTIVITY = "activity"
        private const val PROVIDER = "provider"

        fun fromName(name: String?): SearchScreenTabs = when (name) {
            APP -> App()
            COMPONENT -> Component()
            RULE -> Rule()
            RECEIVER -> Receiver()
            SERVICE -> Service()
            ACTIVITY -> Activity()
            PROVIDER -> Provider()
            else -> throw IllegalArgumentException("Invalid screen name in search page")
        }
    }
}
