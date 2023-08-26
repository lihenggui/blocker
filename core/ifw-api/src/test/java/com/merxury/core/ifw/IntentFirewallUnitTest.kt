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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.Test
class IntentFirewallUnitTest {

    @OptIn(ExperimentalXmlUtilApi::class)
    private val xml = XML {
        policy = DefaultXmlSerializationPolicy(pedantic = false)
        indentString = "   "
    }

    @Test
    fun verifyXmlDeserializer() {
        val fileContent = """
            <rules>
               <activity block="true" log="false">
                  <component-filter name="com.example/com.example.MainActivity" />
               </activity>
               <broadcast block="true" log="false">
                  <component-filter name="com.example/com.example.AlarmReceiver" />
                  <component-filter name="com.example/com.example.BootReceiver" />
                  <component-filter name="com.example/com.example.NotifierReceiver" />
                  <component-filter name="com.example/com.example.TimeZoneReceiver" />
               </broadcast>
               <service block="true" log="false">
                  <component-filter name="com.example/com.example.DaemonService" />
                  <component-filter name="com.example/com.example.ForwardService" />
                  <component-filter name="com.example/com.example.VpnService" />
               </service>
            </rules>
        """.trimIndent()
        val deserializedRule = xml.decodeFromString<Rules>(fileContent)
        val targetRule = Rules(
            activity = Activity(
                componentFilter = mutableSetOf(
                    ComponentFilter("com.example/com.example.MainActivity"),
                ),
            ),
            service = Service(
                componentFilter = mutableSetOf(
                    ComponentFilter("com.example/com.example.DaemonService"),
                    ComponentFilter("com.example/com.example.ForwardService"),
                    ComponentFilter("com.example/com.example.VpnService"),
                ),
            ),
            broadcast = Broadcast(
                componentFilter = mutableSetOf(
                    ComponentFilter("com.example/com.example.AlarmReceiver"),
                    ComponentFilter("com.example/com.example.BootReceiver"),
                    ComponentFilter("com.example/com.example.NotifierReceiver"),
                    ComponentFilter("com.example/com.example.TimeZoneReceiver"),
                ),
            ),
        )
        assert(deserializedRule == targetRule)
    }

    @Test
    fun verifyXmlSerializer() {
        val targetRule = Rules(
            activity = Activity(
                componentFilter = mutableSetOf(
                    ComponentFilter("com.example/com.example.MainActivity"),
                ),
            ),
            service = Service(
                componentFilter = mutableSetOf(
                    ComponentFilter("com.example/com.example.DaemonService"),
                    ComponentFilter("com.example/com.example.ForwardService"),
                    ComponentFilter("com.example/com.example.VpnService"),
                ),
            ),
            broadcast = Broadcast(
                componentFilter = mutableSetOf(
                    ComponentFilter("com.example/com.example.AlarmReceiver"),
                    ComponentFilter("com.example/com.example.BootReceiver"),
                    ComponentFilter("com.example/com.example.NotifierReceiver"),
                    ComponentFilter("com.example/com.example.TimeZoneReceiver"),
                ),
            ),
        )
        val expectedResult = """
            <rules>
               <activity block="true" log="false">
                  <component-filter name="com.example/com.example.MainActivity" />
               </activity>
               <broadcast block="true" log="false">
                  <component-filter name="com.example/com.example.AlarmReceiver" />
                  <component-filter name="com.example/com.example.BootReceiver" />
                  <component-filter name="com.example/com.example.NotifierReceiver" />
                  <component-filter name="com.example/com.example.TimeZoneReceiver" />
               </broadcast>
               <service block="true" log="false">
                  <component-filter name="com.example/com.example.DaemonService" />
                  <component-filter name="com.example/com.example.ForwardService" />
                  <component-filter name="com.example/com.example.VpnService" />
               </service>
            </rules>
        """.trimIndent()
        val serializedXmlContent = xml.encodeToString(targetRule)
        assert(serializedXmlContent == expectedResult)
    }
}
