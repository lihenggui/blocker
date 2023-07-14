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

package com.merxury.core.ifw

import com.merxury.core.ifw.Component.Activity
import com.merxury.core.ifw.Component.Broadcast
import com.merxury.core.ifw.Component.Service
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Format:
 * <rules>
 *   <activity block="[true/false]" log="[true/false]" >
 *     <intent-filter >
 *       <path literal="[literal]" prefix="[prefix]" sglob="[sglob]" />
 *       <auth host="[host]" port="[port]" />
 *       <ssp literal="[literal]" prefix="[prefix]" sglob="[sglob]" />
 *       <scheme name="[name]" />
 *       <type name="[name]" />
 *       <cat name="[category]" />
 *       <action name="[action]" />
 *     </intent-filter>
 *     <component-filter name="[component]" />
 *   </activity>
 * </rules>
 */

@Serializable
@SerialName("rules")
data class Rules(
    private val activity: Activity? = null,
    private val service: Service? = null,
    private val broadcast: Broadcast? = null,
)

// Base class for components (Activity, Broadcast and service)
@Serializable
sealed class Component {
    val block = true
    val log = false
    abstract val intentFilter: IntentFilter?
    abstract val componentFilter: List<ComponentFilter>

    @Serializable
    @SerialName("activity")
    data class Activity(
        override val intentFilter: IntentFilter? = null,
        override val componentFilter: List<ComponentFilter> = listOf(),
    ) : Component()

    @Serializable
    @SerialName("broadcast")
    class Broadcast(
        override val intentFilter: IntentFilter? = null,
        override val componentFilter: List<ComponentFilter> = listOf(),
    ) : Component()

    @Serializable
    @SerialName("service")
    class Service(
        override val intentFilter: IntentFilter? = null,
        override val componentFilter: List<ComponentFilter> = listOf(),
    ) : Component()
}

@Serializable
@SerialName("intent-filter")
data class IntentFilter(
    val path: Path? = null,
    val auth: Auth? = null,
    val ssp: Ssp? = null,
    val scheme: Scheme? = null,
    val type: Type? = null,
    val cat: Cat? = null,
    val action: Action? = null,
)

@Serializable
@SerialName("component-filter")
data class ComponentFilter(
    val name: String,
)

@Serializable
@SerialName("path")
data class Path(
    val literal: String,
    val prefix: String,
    val sglob: String,
)

@Serializable
@SerialName("auth")
data class Auth(
    val host: String,
    val port: String,
)

@Serializable
@SerialName("ssp")
data class Ssp(
    val literal: String,
    val prefix: String,
    val sglob: String,
)

@Serializable
@SerialName("scheme")
data class Scheme(
    val name: String,
)

@Serializable
@SerialName("type")
data class Type(
    val name: String,
)

@Serializable
@SerialName("cat")
data class Cat(
    val name: String,
)

@Serializable
@SerialName("action")
data class Action(
    val name: String,
)
