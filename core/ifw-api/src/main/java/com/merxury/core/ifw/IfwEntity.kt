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
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

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
@XmlSerialName("rules")
data class Rules(
    val activity: Activity = Activity(),
    val broadcast: Broadcast = Broadcast(),
    val service: Service = Service(),
)

// Base class for components (Activity, Broadcast and service)
@Serializable
sealed class Component {
    @XmlElement(value = false)
    val block = true

    @XmlElement(value = false)
    val log = false

    @XmlElement(value = true)
    abstract val intentFilter: IntentFilter?

    @XmlElement(value = true)
    abstract val componentFilter: MutableList<ComponentFilter>

    @Serializable
    @XmlSerialName(value = "activity")
    data class Activity(
        override val intentFilter: IntentFilter? = null,
        override val componentFilter: MutableList<ComponentFilter> = mutableListOf(),
    ) : Component()

    @Serializable
    @XmlSerialName("broadcast")
    data class Broadcast(
        override val intentFilter: IntentFilter? = null,
        override val componentFilter: MutableList<ComponentFilter> = mutableListOf(),
    ) : Component()

    @Serializable
    @XmlSerialName("service")
    data class Service(
        override val intentFilter: IntentFilter? = null,
        override val componentFilter: MutableList<ComponentFilter> = mutableListOf(),
    ) : Component()
}

@Serializable
@XmlSerialName("intent-filter")
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
@XmlSerialName("component-filter")
data class ComponentFilter(
    @XmlElement(value = false)
    val name: String,
)

@Serializable
@XmlSerialName("path")
data class Path(
    @XmlElement(value = false)
    val literal: String,
    @XmlElement(value = false)
    val prefix: String,
    @XmlElement(value = false)
    val sglob: String,
)

@Serializable
@XmlSerialName("auth")
data class Auth(
    @XmlElement(value = false)
    val host: String,
    @XmlElement(value = false)
    val port: String,
)

@Serializable
@XmlSerialName("ssp")
data class Ssp(
    @XmlElement(value = false)
    val literal: String,
    @XmlElement(value = false)
    val prefix: String,
    @XmlElement(value = false)
    val sglob: String,
)

@Serializable
@XmlSerialName("scheme")
data class Scheme(
    @XmlElement(value = false)
    val name: String,
)

@Serializable
@XmlSerialName("type")
data class Type(
    @XmlElement(value = false)
    val name: String,
)

@Serializable
@XmlSerialName("cat")
data class Cat(
    @XmlElement(value = false)
    val name: String,
)

@Serializable
@XmlSerialName("action")
data class Action(
    @XmlElement(value = false)
    val name: String,
)
