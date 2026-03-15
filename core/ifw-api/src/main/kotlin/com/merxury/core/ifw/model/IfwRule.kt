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
 * Represents a single intent firewall rule within a component type section.
 *
 * Each rule targets a specific [IfwComponentType] (activity, broadcast, or service)
 * and contains a filter tree that determines which intents to match.
 *
 * Example XML:
 * ```xml
 * <broadcast block="true" log="true">
 *   <and>
 *     <action equals="android.intent.action.BOOT_COMPLETED" />
 *     <not><sender type="system" /></not>
 *   </and>
 * </broadcast>
 * ```
 *
 * @property componentType the type of component this rule targets
 * @property block whether matching intents should be blocked (`true`) or allowed
 * @property log whether matching intents should be logged to logcat
 * @property filters the list of filters that define what intents this rule matches
 */
data class IfwRule(
    val componentType: IfwComponentType,
    val block: Boolean = true,
    val log: Boolean = true,
    val filters: List<IfwFilter> = emptyList(),
)
