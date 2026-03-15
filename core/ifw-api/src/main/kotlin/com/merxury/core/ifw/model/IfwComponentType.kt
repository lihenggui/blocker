/*
 * Copyright 2025 Blocker
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

package com.merxury.core.ifw.model

/**
 * The three component types that Android Intent Firewall can target.
 *
 * Each type maps to a top-level XML element in the IFW rules file:
 * - `<activity>` for [ACTIVITY]
 * - `<broadcast>` for [BROADCAST]
 * - `<service>` for [SERVICE]
 *
 * Note: Content Providers are intentionally excluded because the Android
 * Intent Firewall does not support filtering provider intents.
 *
 * @property xmlTag the XML tag name used in IFW rule files
 */
enum class IfwComponentType(val xmlTag: String) {
    /** Targets Activity component intents. */
    ACTIVITY("activity"),

    /** Targets Broadcast Receiver intents. */
    BROADCAST("broadcast"),

    /** Targets Service component intents. */
    SERVICE("service"),
    ;

    companion object {
        /**
         * Finds the [IfwComponentType] for the given XML tag name.
         *
         * @param tag the XML tag name (e.g., "activity", "broadcast", "service")
         * @return the matching [IfwComponentType], or `null` if not recognized
         */
        fun fromXmlTag(tag: String): IfwComponentType? = entries.find { it.xmlTag == tag }
    }
}
